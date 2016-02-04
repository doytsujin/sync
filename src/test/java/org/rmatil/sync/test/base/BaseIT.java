package org.rmatil.sync.test.base;

import net.engio.mbassy.bus.MBassador;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.init.objecstore.ObjectStoreFileChangeListener;
import org.rmatil.sync.core.model.RemoteClientLocation;
import org.rmatil.sync.core.syncer.file.FileSyncer;
import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.core.dht.DhtStorageAdapter;
import org.rmatil.sync.persistence.core.local.LocalStorageAdapter;
import org.rmatil.sync.test.config.Config;
import org.rmatil.sync.test.messaging.base.BaseNetworkHandlerTest;
import org.rmatil.sync.test.messaging.base.GlobalEventBusDummyListener;
import org.rmatil.sync.version.api.IObjectStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class BaseIT extends BaseNetworkHandlerTest {

    protected static final Path ROOT_TEST_DIR3 = Paths.get(Config.DEFAULT.getTestRootDir3());
    protected static final Path ROOT_TEST_DIR4 = Paths.get(Config.DEFAULT.getTestRootDir4());

    protected static final int PORT_CLIENT_3 = Config.DEFAULT.getPort3();
    protected static final int PORT_CLIENT_4 = Config.DEFAULT.getPort4();

    protected static final UUID CLIENT_ID_3 = UUID.randomUUID();
    protected static final UUID CLIENT_ID_4 = UUID.randomUUID();

    protected static MBassador<IBusEvent> GLOBAL_EVENT_BUS_3;
    protected static MBassador<IBusEvent> GLOBAL_EVENT_BUS_4;

    protected static GlobalEventBusDummyListener EVENT_BUS_LISTENER_3;
    protected static GlobalEventBusDummyListener EVENT_BUS_LISTENER_4;

    protected static IStorageAdapter STORAGE_ADAPTER_3;
    protected static IObjectStore    OBJECT_STORE_3;

    protected static IStorageAdapter STORAGE_ADAPTER_4;
    protected static IObjectStore    OBJECT_STORE_4;

    protected static IClient CLIENT_3;
    protected static IClient CLIENT_4;

    protected static DhtStorageAdapter DHT_STORAGE_ADAPTER_3;
    protected static DhtStorageAdapter DHT_STORAGE_ADAPTER_4;

    protected static FileSyncer FILE_SYNCER_3;
    protected static FileSyncer FILE_SYNCER_4;

    protected static IEventAggregator EVENT_AGGREGATOR_3;
    protected static IEventAggregator EVENT_AGGREGATOR_4;

    protected static IClientManager CLIENT_MANAGER_3;
    protected static IClientManager CLIENT_MANAGER_4;

    protected static ClientDevice CLIENT_DEVICE_3;
    protected static ClientDevice CLIENT_DEVICE_4;

    @BeforeClass
    public static void setUpIT()
            throws IOException {
        createTestDirs();
        createObjectStoreDirs();

        GLOBAL_EVENT_BUS_3 = BaseNetworkHandlerTest.createGlobalEventBus();
        GLOBAL_EVENT_BUS_4 = BaseNetworkHandlerTest.createGlobalEventBus();

        EVENT_BUS_LISTENER_3 = new GlobalEventBusDummyListener();
        EVENT_BUS_LISTENER_4 = new GlobalEventBusDummyListener();

        GLOBAL_EVENT_BUS_3.subscribe(EVENT_BUS_LISTENER_3);
        GLOBAL_EVENT_BUS_4.subscribe(EVENT_BUS_LISTENER_4);

        STORAGE_ADAPTER_3 = new LocalStorageAdapter(ROOT_TEST_DIR3);
        STORAGE_ADAPTER_4 = new LocalStorageAdapter(ROOT_TEST_DIR4);

        OBJECT_STORE_3 = BaseNetworkHandlerTest.createObjectStore(ROOT_TEST_DIR3);
        OBJECT_STORE_4 = BaseNetworkHandlerTest.createObjectStore(ROOT_TEST_DIR4);

        CLIENT_3 = BaseNetworkHandlerTest.createClient(USER_2, STORAGE_ADAPTER_3, OBJECT_STORE_3, GLOBAL_EVENT_BUS_3, PORT_CLIENT_3, new RemoteClientLocation(
                CLIENT_1.getPeerAddress().inetAddress().getHostName(),
                CLIENT_1.getPeerAddress().isIPv6(),
                CLIENT_1.getPeerAddress().tcpPort()
        ));

        CLIENT_4 = BaseNetworkHandlerTest.createClient(USER_2, STORAGE_ADAPTER_4, OBJECT_STORE_4, GLOBAL_EVENT_BUS_4, PORT_CLIENT_4, new RemoteClientLocation(
                CLIENT_1.getPeerAddress().inetAddress().getHostName(),
                CLIENT_1.getPeerAddress().isIPv6(),
                CLIENT_1.getPeerAddress().tcpPort()
        ));

        DHT_STORAGE_ADAPTER_3 = BaseNetworkHandlerTest.createDhtStorageAdapter(CLIENT_3);
        DHT_STORAGE_ADAPTER_4 = BaseNetworkHandlerTest.createDhtStorageAdapter(CLIENT_4);

        CLIENT_MANAGER_3 = createClientManager(DHT_STORAGE_ADAPTER_3);
        CLIENT_MANAGER_4 = createClientManager(DHT_STORAGE_ADAPTER_4);

        FILE_SYNCER_3 = createFileSyncer(CLIENT_3, DHT_STORAGE_ADAPTER_3, ROOT_TEST_DIR3, OBJECT_STORE_3, GLOBAL_EVENT_BUS_3);
        FILE_SYNCER_4 = createFileSyncer(CLIENT_4, DHT_STORAGE_ADAPTER_4, ROOT_TEST_DIR4, OBJECT_STORE_4, GLOBAL_EVENT_BUS_4);

        GLOBAL_EVENT_BUS_3.subscribe(FILE_SYNCER_3);
        GLOBAL_EVENT_BUS_4.subscribe(FILE_SYNCER_4);

        // Note: start the event aggregator manually in the subclasses
        EVENT_AGGREGATOR_3 = createEventAggregator(ROOT_TEST_DIR3, OBJECT_STORE_3, FILE_SYNCER_3, GLOBAL_EVENT_BUS_3);
        EVENT_AGGREGATOR_4 = createEventAggregator(ROOT_TEST_DIR4, OBJECT_STORE_4, FILE_SYNCER_4, GLOBAL_EVENT_BUS_4);

        EVENT_AGGREGATOR_3.addListener(new ObjectStoreFileChangeListener(OBJECT_STORE_3));
        EVENT_AGGREGATOR_4.addListener(new ObjectStoreFileChangeListener(OBJECT_STORE_4));

        CLIENT_DEVICE_3 = new ClientDevice(USERNAME, CLIENT_ID_3, CLIENT_3.getPeerAddress());
        CLIENT_DEVICE_4 = new ClientDevice(USERNAME, CLIENT_ID_4, CLIENT_4.getPeerAddress());
    }

    @AfterClass
    public static void tearDownIT() {
        EVENT_AGGREGATOR_3.stop();
        EVENT_AGGREGATOR_4.stop();

        CLIENT_3.shutdown();
        CLIENT_4.shutdown();

        deleteTestDirs();
    }

    /**
     * Creates the test directories
     *
     * @throws IOException If creating the directories failed
     */
    protected static void createTestDirs()
            throws IOException {
        if (! ROOT_TEST_DIR3.toFile().exists()) {
            Files.createDirectory(ROOT_TEST_DIR3);
        }

        if (! ROOT_TEST_DIR4.toFile().exists()) {
            Files.createDirectory(ROOT_TEST_DIR4);
        }
    }

    /**
     * Creates the .sync folders
     *
     * @throws IOException If creating failed
     */
    protected static void createObjectStoreDirs()
            throws IOException {
        Path syncFolder3 = ROOT_TEST_DIR3.resolve(SYNC_FOLDER_NAME);
        Path syncFolder4 = ROOT_TEST_DIR4.resolve(SYNC_FOLDER_NAME);

        if (! syncFolder3.toFile().exists()) {
            Files.createDirectory(syncFolder3);
        }

        if (! syncFolder4.toFile().exists()) {
            Files.createDirectory(syncFolder4);
        }
    }

    /**
     * Deletes the test directories and all contents in them
     */
    protected static void deleteTestDirs() {
        if (ROOT_TEST_DIR3.toFile().exists()) {
            delete(ROOT_TEST_DIR3.toFile());
        }

        if (ROOT_TEST_DIR4.toFile().exists()) {
            delete(ROOT_TEST_DIR4.toFile());
        }
    }
}
