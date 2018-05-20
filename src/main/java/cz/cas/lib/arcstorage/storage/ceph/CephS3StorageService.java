package cz.cas.lib.arcstorage.storage.ceph;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import cz.cas.lib.arcstorage.domain.entity.Storage;
import cz.cas.lib.arcstorage.dto.*;
import cz.cas.lib.arcstorage.exception.GeneralException;
import cz.cas.lib.arcstorage.storage.StorageService;
import cz.cas.lib.arcstorage.storage.StorageUtils;
import cz.cas.lib.arcstorage.storage.exception.FileCorruptedAfterStoreException;
import cz.cas.lib.arcstorage.storage.exception.FileDoesNotExistException;
import cz.cas.lib.arcstorage.storage.exception.IOStorageException;
import cz.cas.lib.arcstorage.storage.exception.StorageException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.NullInputStream;

import java.io.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static cz.cas.lib.arcstorage.storage.StorageUtils.toXmlId;

/**
 * Implementation of {@link StorageService} for the Ceph accessed over S3.
 * <p>
 * Metadata are stored in a separate metadata object, which id is idOfTheObject.meta
 * </p>
 * Fulfillment of the requirements on the metadata storing specified by the interface:
 * <ul>
 * <li>initial checksum of the object: stored in metadata object</li>
 * <li>creation time of the object: stored in metadata object</li>
 * <li>state of object matching {@link ObjectState}: stored in metadata object</li>
 * <li>for AIP XML its version and ID of SIP: id of XML is in form aipId_xml_versionNumber</li>
 * </ul>
 */
@Slf4j
public class CephS3StorageService implements StorageService {

    //keys must not contain dash or camelcase
    static final String STATE_KEY = "state";
    static final String CREATED_KEY = "created";

    private Storage storage;
    private String userAccessKey;
    private String userSecretKey;
    //not used for now
    private String region;
    private int connectionTimeout;

    public CephS3StorageService(Storage storage, String userAccessKey, String userSecretKey, String region, int connectionTimeout) {
        this.storage = storage;
        this.userAccessKey = userAccessKey;
        this.userSecretKey = userSecretKey;
        this.region = region;
        this.connectionTimeout = connectionTimeout;
    }

    @Override
    public Storage getStorage() {
        return storage;
    }

    @Override
    public void storeAip(AipDto aipDto, AtomicBoolean rollback) throws StorageException {
        AmazonS3 s3 = connect();
        SipDto sip = aipDto.getSip();
        ArchivalObjectDto xml = aipDto.getXml();
        storeFile(s3, sip.getId(), sip.getInputStream(), sip.getChecksum(), rollback);
        storeFile(s3, xml.getStorageId(), xml.getInputStream(), xml.getChecksum(), rollback);
    }

    @Override
    public AipRetrievalResource getAip(String aipId, Integer... xmlVersions) throws FileDoesNotExistException {
        AmazonS3 s3 = connect();
        checkFileExists(s3, aipId);
        AipRetrievalResource aip = new AipRetrievalResource(new ClosableS3(s3));
        aip.setSip(s3.getObject(storage.getLocation(), aipId).getObjectContent());
        for (Integer xmlVersion : xmlVersions) {
            String xmlId = toXmlId(aipId, xmlVersion);
            checkFileExists(s3, xmlId);
            aip.addXml(xmlVersion, s3.getObject(storage.getLocation(), xmlId).getObjectContent());
        }
        return aip;
    }

    @Override
    public void storeObject(ArchivalObjectDto archivalObject, AtomicBoolean rollback) throws StorageException {
        AmazonS3 s3 = connect();
        storeFile(s3, archivalObject.getStorageId(), archivalObject.getInputStream(), archivalObject.getChecksum(), rollback);
    }

    @Override
    public ObjectRetrievalResource getObject(String id) throws FileDoesNotExistException {
        AmazonS3 s3 = connect();
        checkFileExists(s3, id);
        return new ObjectRetrievalResource(
                s3.getObject(storage.getLocation(), id).getObjectContent(),
                new ClosableS3(s3));
    }

    @Override
    public void deleteSip(String sipId) throws StorageException {
        AmazonS3 s3 = connect();
        String metadataId = toMetadataObjectId(sipId);
        ObjectMetadata metadata = s3.getObjectMetadata(storage.getLocation(), metadataId);
        metadata.addUserMetadata(STATE_KEY, ObjectState.PROCESSING.toString());
        s3.putObject(storage.getLocation(), toMetadataObjectId(sipId), new NullInputStream(0), metadata);
        s3.deleteObject(storage.getLocation(), sipId);
        metadata.addUserMetadata(STATE_KEY, ObjectState.DELETED.toString());
        s3.putObject(storage.getLocation(), toMetadataObjectId(sipId), new NullInputStream(0), metadata);
    }

    @Override
    public void remove(String sipId) throws StorageException {
        AmazonS3 s3 = connect();
        String metadataId = toMetadataObjectId(sipId);
        ObjectMetadata objectMetadata = s3.getObjectMetadata(storage.getLocation(), metadataId);
        objectMetadata.addUserMetadata(STATE_KEY, ObjectState.REMOVED.toString());
        s3.putObject(storage.getLocation(), toMetadataObjectId(sipId), new NullInputStream(0), objectMetadata);
    }

    @Override
    public void renew(String sipId) throws StorageException {
        AmazonS3 s3 = connect();
        String metadataId = toMetadataObjectId(sipId);
        ObjectMetadata objectMetadata = s3.getObjectMetadata(storage.getLocation(), metadataId);
        objectMetadata.addUserMetadata(STATE_KEY, ObjectState.ARCHIVED.toString());
        s3.putObject(storage.getLocation(), toMetadataObjectId(sipId), new NullInputStream(0), objectMetadata);
    }


    @Override
    public void rollbackAip(String sipId) throws StorageException {
        AmazonS3 s3 = connect();
        rollbackFile(s3, sipId);
        rollbackFile(s3, toXmlId(sipId, 1));
    }

    @Override
    public void rollbackObject(String id) throws StorageException {
        AmazonS3 s3 = connect();
        rollbackFile(s3, id);
    }

    @Override
    public AipStateInfoDto getAipInfo(String aipId, Checksum sipChecksum, ObjectState objectState, Map<Integer, Checksum> xmlVersions) throws FileDoesNotExistException {
        AmazonS3 s3 = connect();
        AipStateInfoDto info = new AipStateInfoDto(storage.getName(), storage.getStorageType(), objectState, sipChecksum, true);
        if (objectState == ObjectState.ARCHIVED || objectState == ObjectState.REMOVED) {
            checkFileExists(s3, aipId);
            S3Object sipObject = s3.getObject(storage.getLocation(), aipId);
            Checksum storageFileChecksum = StorageUtils.computeChecksum(sipObject.getObjectContent(),
                    sipChecksum.getType());
            info.setSipStorageChecksum(storageFileChecksum);
            info.setConsistent(sipChecksum.equals(storageFileChecksum));
        } else {
            info.setSipStorageChecksum(null);
            info.setConsistent(false);
        }

        for (Integer version : xmlVersions.keySet()) {
            String xmlId = toXmlId(aipId, version);
            checkFileExists(s3, xmlId);
            S3Object xmlObject = s3.getObject(storage.getLocation(), xmlId);
            Checksum dbChecksum = xmlVersions.get(version);
            Checksum storageFileChecksum = StorageUtils.computeChecksum(xmlObject.getObjectContent(),
                    dbChecksum.getType());
            info.addXmlInfo(new XmlStateInfoDto(version, dbChecksum.equals(storageFileChecksum), storageFileChecksum,
                    dbChecksum));
        }
        return info;
    }

    @Override
    public StorageStateDto getStorageState() throws StorageException {
        //bucket quota, user quota, whole cluster, number of objects ...
        throw new UnsupportedOperationException("not supported yet");
    }

    @Override
    public boolean testConnection() {
        try {
            AmazonS3 s3 = connect();
            s3.getBucketLocation(storage.getLocation());
        } catch (Exception e) {
            log.error(storage.getName() + " unable to connect: " + e.getClass() + " " + e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Stores file and then reads it and verifies its fixity.
     * <p>
     * If rollback is set to true by another thread, this method returns ASAP (without throwing exception), leaving the file uncompleted but closing stream.  Uncompleted files are to be cleaned during rollback.
     * </p>
     * <p>
     * In case of any exception, rollback flag is set to true.
     * </p>
     */
    void storeFile(AmazonS3 s3, String id, InputStream stream, Checksum checksum, AtomicBoolean rollback) throws FileCorruptedAfterStoreException, IOStorageException {
        if (rollback.get())
            return;
        try (BufferedInputStream bis = new BufferedInputStream(stream)) {
            InitiateMultipartUploadRequest initReq = new InitiateMultipartUploadRequest(storage.getLocation(), id, new ObjectMetadata());
            InitiateMultipartUploadResult initRes = s3.initiateMultipartUpload(initReq);

            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.addUserMetadata(checksum.getType().toString(), checksum.getValue());
            objectMetadata.addUserMetadata(STATE_KEY, ObjectState.PROCESSING.toString());
            objectMetadata.addUserMetadata(CREATED_KEY, LocalDateTime.now().toString());
            objectMetadata.setContentLength(0);
            PutObjectRequest metadataPutRequest = new PutObjectRequest(storage.getLocation(), toMetadataObjectId(id), new NullInputStream(0), objectMetadata);
            s3.putObject(metadataPutRequest);

            byte[] buff = new byte[8 * 1024 * 1024];
            int read;
            boolean last;
            List<PartETag> partETags = new ArrayList<>();
            int partNumber = 0;
            do {
                if (rollback.get())
                    return;
                partNumber++;
                read = bis.read(buff);
                last = read != buff.length;
                long partSize = Math.min(read, buff.length);
                if (last)
                    buff = Arrays.copyOf(buff, read);
                UploadPartRequest uploadPartRequest = new UploadPartRequest()
                        .withBucketName(storage.getLocation())
                        .withUploadId(initRes.getUploadId())
                        .withKey(id)
                        .withInputStream(new ByteArrayInputStream(buff))
                        .withPartNumber(partNumber)
                        .withPartSize(partSize)
                        .withLastPart(last);
                UploadPartResult uploadPartResult = s3.uploadPart(uploadPartRequest);
                Checksum partChecksum = computeChecksumRollbackAware(new ByteArrayInputStream(buff), ChecksumType.MD5, rollback);
                if (partChecksum == null)
                    return;
                if (!partChecksum.getValue().equalsIgnoreCase(uploadPartResult.getETag()))
                    throw new FileCorruptedAfterStoreException("S3 - part of multipart file", new Checksum(ChecksumType.MD5, uploadPartResult.getETag()), partChecksum);
                partETags.add(uploadPartResult.getPartETag());
            } while (!last);
            CompleteMultipartUploadRequest completeReq = new CompleteMultipartUploadRequest(storage.getLocation(), id, initRes.getUploadId(), partETags);
            s3.completeMultipartUpload(completeReq);
            objectMetadata.addUserMetadata(STATE_KEY, ObjectState.ARCHIVED.toString());
            s3.putObject(metadataPutRequest);
        } catch (Exception e) {
            rollback.set(true);
            if (e instanceof IOException)
                throw new IOStorageException(e);
            if (e instanceof FileCorruptedAfterStoreException)
                throw (FileCorruptedAfterStoreException) e;
            throw new GeneralException(e);
        }
    }

    void rollbackFile(AmazonS3 s3, String id) {
        ObjectMetadata objectMetadata;
        String metadataId = toMetadataObjectId(id);
        boolean metadataExists = s3.doesObjectExist(storage.getLocation(), metadataId);
        if (!metadataExists)
            objectMetadata = new ObjectMetadata();
        else
            objectMetadata = s3.getObjectMetadata(storage.getLocation(), metadataId);
        objectMetadata.addUserMetadata(STATE_KEY, ObjectState.PROCESSING.toString());
        s3.putObject(storage.getLocation(), metadataId, new NullInputStream(0), objectMetadata);
        List<MultipartUpload> multipartUploads = s3.listMultipartUploads(new ListMultipartUploadsRequest(storage.getLocation()).withPrefix(id)).getMultipartUploads();
        if (multipartUploads.size() == 1)
            s3.abortMultipartUpload(new AbortMultipartUploadRequest(storage.getLocation(), id, multipartUploads.get(0).getUploadId()));
        else if (multipartUploads.size() > 1)
            throw new GeneralException("unexpected error during rollback of file: " + id + " : there are more than one upload in progress");
        s3.deleteObject(storage.getLocation(), id);
        objectMetadata.addUserMetadata(STATE_KEY, ObjectState.ROLLED_BACK.toString());
        s3.putObject(storage.getLocation(), metadataId, new NullInputStream(0), objectMetadata);
    }

    AmazonS3 connect() {
        AWSCredentials credentials = new BasicAWSCredentials(userAccessKey, userSecretKey);
        AWSStaticCredentialsProvider provider = new AWSStaticCredentialsProvider(credentials);
        ClientConfiguration clientConfig = new ClientConfiguration();
        //force usage of AWS signature v2 instead of v4 to enable multipart uploads (v4 does not work with multipart upload for now)
        clientConfig.setSignerOverride("S3SignerType");
        clientConfig.setProtocol(Protocol.HTTP);
        clientConfig.setConnectionTimeout(connectionTimeout);
        AmazonS3 conn = AmazonS3ClientBuilder
                .standard()
                .withCredentials(provider)
                .withClientConfiguration(clientConfig)
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(storage.getHost() + ":" + storage.getPort(), region))
                .build();
        return conn;
    }

    private void checkFileExists(AmazonS3 s3, String id) throws FileDoesNotExistException {
        boolean exist = s3.doesObjectExist(storage.getLocation(), id);
        if (!exist)
            throw new FileDoesNotExistException("bucket: " + storage.getLocation() + " storageId: " + id);
    }

    String toMetadataObjectId(String objId) {
        return objId + ".meta";
    }

    private class ClosableS3 implements Closeable {

        private AmazonS3 s3;

        public ClosableS3(AmazonS3 s3) {
            this.s3 = s3;
        }

        @Override
        public void close() throws IOException {
            s3.shutdown();
        }
    }
}
