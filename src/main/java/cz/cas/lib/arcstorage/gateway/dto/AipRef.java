package cz.cas.lib.arcstorage.gateway.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@NoArgsConstructor
public class AipRef {
    @Setter
    @Getter
    private ArchiveFileRef sip;

    private List<XmlRef> xmls = new ArrayList<>();

    public AipRef(AipRef aipRef, FileRef sipIs, FileRef xmlIs) {
        sip = new ArchiveFileRef(aipRef.getSip().getId(), sipIs, aipRef.getSip().getChecksum());
        xmls.add(new XmlRef(aipRef.getXml().getId(), xmlIs, aipRef.getXml().getChecksum(), aipRef.getXml().getVersion()));
    }

    public List<XmlRef> getXmls() {
        return Collections.unmodifiableList(xmls);
    }

    public XmlRef getXml(int index) {
        return xmls.get(index);
    }

    public XmlRef getXml() {
        return xmls.get(0);
    }

    public void addXml(XmlRef xml) {
        xmls.add(xml);
    }
}
