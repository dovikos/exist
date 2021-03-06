package org.exist.test;

import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.storage.journal.Journal;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.FileUtils;
import org.junit.rules.ExternalResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Exist embedded Server Rule for JUnit
 */
public class ExistEmbeddedServer extends ExternalResource {

    private final Optional<String> instanceName;
    private final Optional<Path> configFile;
    private final Optional<Properties> configProperties;
    private final boolean useTemporaryStorage;
    private Optional<Path> temporaryStorage = Optional.empty();

    private BrokerPool pool = null;

    public ExistEmbeddedServer() {
        this(null, null, null, false);
    }

    public ExistEmbeddedServer(final boolean useTemporaryStorage) {
        this(null, null, null, useTemporaryStorage);
    }

    public ExistEmbeddedServer(final Properties configProperties) {
        this(null, null, configProperties, false);
    }

    public ExistEmbeddedServer(final String instanceName, final Path configFile) {
        this(instanceName, configFile, null, false);
    }

    public ExistEmbeddedServer(final String instanceName, final Path configFile, final Properties configProperties) {
        this(instanceName, configFile, configProperties, false);
    }

    public ExistEmbeddedServer(final String instanceName, final Path configFile, final Properties configProperties, final boolean useTemporaryStorage) {
        this.instanceName = Optional.ofNullable(instanceName);
        this.configFile = Optional.ofNullable(configFile);
        this.configProperties = Optional.ofNullable(configProperties);
        this.useTemporaryStorage = useTemporaryStorage;
    }

    @Override
    protected void before() throws Throwable {
        startDb();
        super.before();
    }

    private void startDb() throws DatabaseConfigurationException, EXistException, IOException {
        if(pool == null) {

            final String name = instanceName.orElse(BrokerPool.DEFAULT_INSTANCE_NAME);

            final Optional<Path> home = Optional.ofNullable(System.getProperty("exist.home", System.getProperty("user.dir"))).map(Paths::get);
            final Path confFile = configFile.orElseGet(() -> ConfigurationHelper.lookup("conf.xml", home));

            final Configuration config;
            if(confFile.isAbsolute() && Files.exists(confFile)) {
                //TODO(AR) is this correct?
                config = new Configuration(confFile.toAbsolutePath().toString());
            } else {
                config = new Configuration(FileUtils.fileName(confFile), home);
            }

            // override any specified config properties
            if(configProperties.isPresent()) {
                for(final Map.Entry<Object, Object> configProperty : configProperties.get().entrySet()) {
                    config.setProperty(configProperty.getKey().toString(), configProperty.getValue());
                }
            }

            if(useTemporaryStorage) {
                this.temporaryStorage = Optional.of(Files.createTempDirectory("org.exist.test.ExistEmbeddedServer"));
                config.setProperty(BrokerPool.PROPERTY_DATA_DIR, temporaryStorage.get());
                config.setProperty(Journal.RECOVERY_JOURNAL_DIR_ATTRIBUTE, temporaryStorage.get());
                System.out.println("Using temporary storage location: " + temporaryStorage.get().toAbsolutePath().toString());
            }

            BrokerPool.configure(name,1, 5, config, Optional.empty());
            this.pool = BrokerPool.getInstance(name);
        } else {
            throw new IllegalStateException("ExistEmbeddedServer already running");
        }
    }

    public BrokerPool getBrokerPool() {
        return pool;
    }

    public void restart() throws EXistException, DatabaseConfigurationException, IOException {
        if(pool != null) {
            stopDb();
            startDb();
        } else {
            throw new IllegalStateException("ExistEmbeddedServer already stopped");
        }
    }

    @Override
    protected void after() {
        stopDb();
        super.after();
    }

    private void stopDb() {
        if(pool != null) {
            pool.shutdown();

            // clear instance variables
            pool = null;

            if(useTemporaryStorage && temporaryStorage.isPresent()) {
                FileUtils.deleteQuietly(temporaryStorage.get());
                temporaryStorage = Optional.empty();
            }
        } else {
            throw new IllegalStateException("ExistEmbeddedServer already stopped");
        }
    }
}
