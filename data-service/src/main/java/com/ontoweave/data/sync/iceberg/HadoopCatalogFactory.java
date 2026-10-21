package com.ontoweave.data.sync.iceberg;

import com.ontoweave.data.common.config.SyncProperties;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.hadoop.HadoopCatalog;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;

@Component
public class HadoopCatalogFactory {

    private final SyncProperties properties;

    public HadoopCatalogFactory(SyncProperties properties) {
        this.properties = properties;
    }

    public HadoopCatalog open() {
        Configuration configuration = new Configuration();
        HadoopCatalog catalog = new HadoopCatalog(configuration, properties.icebergWarehouse());
        catalog.initialize("ontoweave", Map.of("warehouse", properties.icebergWarehouse()));
        return catalog;
    }

    public <T> Optional<T> withTable(
            String namespace,
            String table,
            TableFunction<T> function
    ) {
        try (HadoopCatalog catalog = open()) {
            TableIdentifier identifier = TableIdentifier.of(namespace, table);
            if (!catalog.tableExists(identifier)) {
                return Optional.empty();
            }
            return Optional.ofNullable(function.apply(catalog.loadTable(identifier)));
        } catch (CancellationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IcebergCatalogException(
                    "读取 Iceberg 表失败: " + namespace + "." + table,
                    exception
            );
        }
    }

    @FunctionalInterface
    public interface TableFunction<T> {
        T apply(Table table) throws Exception;
    }
}
