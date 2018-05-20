package cz.cas.lib.arcstorage.api;

import cz.cas.lib.arcstorage.domain.entity.Storage;
import cz.cas.lib.arcstorage.domain.store.StorageStore;
import cz.cas.lib.arcstorage.domain.store.Transactional;
import cz.cas.lib.arcstorage.dto.StorageUpdateDto;
import cz.cas.lib.arcstorage.exception.MissingObject;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import javax.validation.Valid;
import java.util.Collection;

import static cz.cas.lib.arcstorage.util.Utils.notNull;

@RestController
@RequestMapping("/api/administration")
public class AdministrationApi {

    private StorageStore storageStore;

    @ApiOperation(value = "Returns all attached logical storages.", response = Storage.class, responseContainer = "list")
    @Transactional
    @RequestMapping(value = "/storage", method = RequestMethod.GET)
    public Collection<Storage> getAll() {
        return storageStore.findAll();
    }

    @ApiOperation(value = "Returns logical storage with specified ID.", response = Storage.class)
    @Transactional
    @RequestMapping(value = "/storage/{id}", method = RequestMethod.GET)
    public Storage getOne(
            @ApiParam(value = "id of the logical storage", required = true) @PathVariable("id") String id) {
        return storageStore.find(id);
    }

    @ApiOperation(value = "Attaches new logical storage.", response = Storage.class, notes = "" +
            "* Local FS/ZFS or FS/ZFS over NFS storage configuration:\n" +
            "  * {\n" +
            "      \"host\": \"localhost\",\n" +
            "      \"location\": \"*path to data folder*\",\n" +
            "      \"name\": \"local storage\",\n" +
            "      \"priority\": 1,\n" +
            "      \"storageType\": \"FS\"\n" +
            "    }\n" +
            "* Ceph storage configuration:\n" +
            "  * {\n" +
            "      \"config\": \"{\\\"adapterType\\\":\\\"S3\\\", \\\"userKey\\\":\\\"*RGW S3 user key*\\\",\\\"userSecret\\\":\\\"*RGW S3 user secret*\\\"}\",\n" +
            "      \"host\": \"*ip address of the RGW instance*\",\n" +
            "      \"location\": \"*RGW bucket name*\",\n" +
            "      \"name\": \"ceph storage\",\n" +
            "      \"port\": *port of the RGW instance*,\n" +
            "      \"priority\": 1,\n" +
            "      \"storageType\": \"CEPH\"\n" +
            "    }\n" +
            "* Remote FS/ZFS over SFTP configuration:\n" +
            "  * {\n" +
            "        \"host\": \"*ip address of the remote server*\",\n" +
            "        \"location\": \"*path to data folder*\",\n" +
            "        \"name\": \"sftp storage\",\n" +
            "        \"port\": *SSH port*,\n" +
            "        \"priority\": 1,\n" +
            "        \"storageType\": \"*for now, FS and ZFS works the same*\"\n" +
            "      }\n" +
            "* In order to produce the right JSON, Windows paths separators has to be escaped (\"location\":\"d:\\test\" -> \"location\":\"d:\\\\\\test\")\n" +
            "* The reachable attribute is managed by the application itself.")
    @Transactional
    @RequestMapping(value = "/storage", method = RequestMethod.POST)
    public Storage create(
            @ApiParam(value = "logical storage entity", required = true) @RequestBody @Valid Storage storage) {
        storageStore.save(storage);
        return storage;
    }

    @ApiOperation(value = "Updates a logical storage.", response = Storage.class)
    @Transactional
    @RequestMapping(value = "/storage/update", method = RequestMethod.POST)
    public Storage update(
            @ApiParam(value = "update DTO of the logical storage entity", required = true) @RequestBody @Valid StorageUpdateDto storageUpdateDto) {
        Storage storage = storageStore.find(storageUpdateDto.getId());
        notNull(storage, () -> new MissingObject(Storage.class, storageUpdateDto.getId()));
        storage.setName(storageUpdateDto.getName());
        storage.setPriority(storageUpdateDto.getPriority());
        storage.setNote(storageUpdateDto.getNote());
        storageStore.save(storage);
        return storage;
    }

    @ApiOperation(value = "Removes a logical storage.")
    @Transactional
    @RequestMapping(value = "/storage/{id}", method = RequestMethod.DELETE)
    public void delete(
            @ApiParam(value = "id of the logical storage", required = true) @PathVariable("id") String id) {
        storageStore.delete(new Storage(id));
    }

    @Inject
    public void setStorageStore(StorageStore storageStore) {
        this.storageStore = storageStore;
    }
}