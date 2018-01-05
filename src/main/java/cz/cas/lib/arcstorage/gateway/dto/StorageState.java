package cz.cas.lib.arcstorage.gateway.dto;

import cz.cas.lib.arcstorage.domain.StorageConfig;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class StorageState {
    private StorageConfig storageConfig;
}
