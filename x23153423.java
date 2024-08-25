package org.fog.test.perfeval;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.entities.FogDeviceCharacteristics;

public class x23153423 {
    static List<FogDevice> fogDevices = new ArrayList<>();
    static List<Sensor> sensors = new ArrayList<>();
    static List<Actuator> actuators = new ArrayList<>();
    private static boolean CLOUD = true;

    public static void main(String[] args) {
        try {
            Log.disable();
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            CloudSim.init(num_user, calendar, false);

            String appId = "wearable_health_monitor";
            FogBroker broker = new FogBroker("broker");

            Application application = createApplication(appId, broker.getId());

            createFogDevices(broker.getId(), appId);

            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
            for (FogDevice device : fogDevices) {
                if (device.getName().startsWith("edge")) {
                    moduleMapping.addModuleToDevice("body_temperature_module", device.getName());
                    moduleMapping.addModuleToDevice("heart_rate_module", device.getName());
                    moduleMapping.addModuleToDevice("spo2_module", device.getName());
                    moduleMapping.addModuleToDevice("accelerometer_module", device.getName());
                    moduleMapping.addModuleToDevice("blood_pressure_module", device.getName());
                    moduleMapping.addModuleToDevice("gyroscope_module", device.getName());
                }
            }

            if (CLOUD) {
                moduleMapping.addModuleToDevice("body_temperature_module", "cloud");
                moduleMapping.addModuleToDevice("heart_rate_module", "cloud");
                moduleMapping.addModuleToDevice("spo2_module", "cloud");
                moduleMapping.addModuleToDevice("accelerometer_module", "cloud");
                moduleMapping.addModuleToDevice("blood_pressure_module", "cloud");
                moduleMapping.addModuleToDevice("gyroscope_module", "cloud");
                moduleMapping.addModuleToDevice("analyzer", "cloud");
            }
            moduleMapping.addModuleToDevice("analyzer", "cloud");

            Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);
            controller.submitApplication(application,
                    (CLOUD) ? (new ModulePlacementMapping(fogDevices, application, moduleMapping))
                            : (new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping)));

            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            Log.printLine("Wearable Device Simulation finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happened");
        }
    }

    private static void createFogDevices(int userId, String appId) throws Exception {
        // Create the cloud device
        FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16 * 103, 16 * 83.25);
        cloud.setParentId(-1);
        fogDevices.add(cloud);

        // Create the proxy server (single gateway)
        FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333);
        proxy.setParentId(cloud.getId());
        proxy.setUplinkLatency(100);
        fogDevices.add(proxy);

        // Number of area gateways
        int numAreaGateways = 2;

        // Number of edge devices per area gateway
        int numEdgeDevicesPerAreaGateway = 2;

        // Create multiple area gateways and connect edge devices to each
        for (int i = 0; i < numAreaGateways; i++) {
            FogDevice areaGateway = createFogDevice("area-gateway-" + i, 2800, 4000, 10000, 10000, 2, 0.0, 107.339, 83.4333);
            areaGateway.setParentId(proxy.getId());
            areaGateway.setUplinkLatency(50); // Latency between area gateway and proxy
            fogDevices.add(areaGateway);

            // Create edge devices for this area gateway
            for (int j = 0; j < numEdgeDevicesPerAreaGateway; j++) {
                FogDevice edgeDevice = createFogDevice("edge-device-" + i + "-" + j, 2800, 4000, 1000, 10000, 3, 0.0, 107.339, 83.4333);
                edgeDevice.setParentId(areaGateway.getId());
                edgeDevice.setUplinkLatency(2); // Latency between edge device and area gateway
                fogDevices.add(edgeDevice);
            }
        }

        // link sensors and actuators to the edge devices
        linkSensorsAndActuatorsToFogDevices(userId, appId);
    }

    private static void linkSensorsAndActuatorsToFogDevices(int userId, String appId) {
        //  attach sensors and actuators
        for (FogDevice edgeDevice : fogDevices) {
            if (edgeDevice.getName().startsWith("edge-device")) {

                Sensor bodyTemperatureSensor = new Sensor("bodyTemperatureSensor-" + edgeDevice.getName(), "BODY_TEMPERATURE", userId, appId, new DeterministicDistribution(5));
                bodyTemperatureSensor.setGatewayDeviceId(edgeDevice.getId());
                sensors.add(bodyTemperatureSensor);

                Sensor heartRateSensor = new Sensor("heartRateSensor-" + edgeDevice.getName(), "HEART_RATE", userId, appId, new DeterministicDistribution(5));
                heartRateSensor.setGatewayDeviceId(edgeDevice.getId());
                sensors.add(heartRateSensor);

                Sensor spo2Sensor = new Sensor("spo2Sensor-" + edgeDevice.getName(), "SPO2", userId, appId, new DeterministicDistribution(5));
                spo2Sensor.setGatewayDeviceId(edgeDevice.getId());
                sensors.add(spo2Sensor);

                Sensor accelerometerSensor = new Sensor("accelerometerSensor-" + edgeDevice.getName(), "ACCELEROMETER", userId, appId, new DeterministicDistribution(5));
                accelerometerSensor.setGatewayDeviceId(edgeDevice.getId());
                sensors.add(accelerometerSensor);

                Sensor bloodPressureSensor = new Sensor("bloodPressureSensor-" + edgeDevice.getName(), "BLOOD_PRESSURE", userId, appId, new DeterministicDistribution(5));
                bloodPressureSensor.setGatewayDeviceId(edgeDevice.getId());
                sensors.add(bloodPressureSensor);

                Sensor gyroscopeSensor = new Sensor("gyroscopeSensor-" + edgeDevice.getName(), "GYROSCOPE", userId, appId, new DeterministicDistribution(5));
                gyroscopeSensor.setGatewayDeviceId(edgeDevice.getId());
                sensors.add(gyroscopeSensor);

                Actuator alertActuator = new Actuator("alertActuator-" + edgeDevice.getName(), userId, appId, "ALERT");
                alertActuator.setGatewayDeviceId(edgeDevice.getId());
                actuators.add(alertActuator);
            }
        }
    }

    private static FogDevice createFogDevice(String nodeName, long mips, int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {
        List<Pe> peList = new ArrayList<>();
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));
        int hostId = FogUtils.generateEntityId();
        long storage = 1000000;
        int bw = 10000;

        PowerHost host = new PowerHost(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(bw),
                storage,
                peList,
                new StreamOperatorScheduler(peList),
                new FogLinearPowerModel(busyPower, idlePower)
        );

        List<Host> hostList = new ArrayList<>();
        hostList.add(host);

        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double time_zone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;
        LinkedList<Storage> storageList = new LinkedList<>();

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                arch, os, vmm, host, time_zone, cost, costPerMem,
                costPerStorage, costPerBw);

        FogDevice fogdevice = null;
        try {
            fogdevice = new FogDevice(nodeName, characteristics,
                    new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
        } catch (Exception e) {
            e.printStackTrace();
        }

        fogdevice.setLevel(level);
        return fogdevice;
    }

    private static Application createApplication(String appId, int userId) {
        Application application = Application.createApplication(appId, userId);

        application.addAppModule("body_temperature_module", 10);
        application.addAppModule("heart_rate_module", 10);
        application.addAppModule("spo2_module", 10);
        application.addAppModule("accelerometer_module", 10);
        application.addAppModule("blood_pressure_module", 10);
        application.addAppModule("gyroscope_module", 10);
        application.addAppModule("analyzer", 10);

        application.addAppEdge("BODY_TEMPERATURE", "body_temperature_module", 1000, 2000, "BODY_TEMPERATURE", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("HEART_RATE", "heart_rate_module", 1000, 2000, "HEART_RATE", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("SPO2", "spo2_module", 1000, 2000, "SPO2", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("ACCELEROMETER", "accelerometer_module", 1000, 2000, "ACCELEROMETER", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("BLOOD_PRESSURE", "blood_pressure_module", 1000, 2000, "BLOOD_PRESSURE", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("GYROSCOPE", "gyroscope_module", 1000, 2000, "GYROSCOPE", Tuple.UP, AppEdge.SENSOR);

        application.addAppEdge("body_temperature_module", "analyzer", 500, 100, "TEMP_ALERT", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("heart_rate_module", "analyzer", 500, 100, "HEART_ALERT", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("spo2_module", "analyzer", 500, 100, "SPO2_ALERT", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("accelerometer_module", "analyzer", 500, 100, "ACCEL_ALERT", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("blood_pressure_module", "analyzer", 500, 100, "BP_ALERT", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("gyroscope_module", "analyzer", 500, 100, "GYRO_ALERT", Tuple.UP, AppEdge.MODULE);

        application.addAppEdge("analyzer", "ALERT", 100, 50, "ALERT_SIGNAL", Tuple.DOWN, AppEdge.ACTUATOR);

        application.addTupleMapping("body_temperature_module", "BODY_TEMPERATURE", "TEMP_ALERT", new FractionalSelectivity(0.7));
        application.addTupleMapping("heart_rate_module", "HEART_RATE", "HEART_ALERT", new FractionalSelectivity(0.7));
        application.addTupleMapping("spo2_module", "SPO2", "SPO2_ALERT", new FractionalSelectivity(0.5));
        application.addTupleMapping("accelerometer_module", "ACCELEROMETER", "ACCEL_ALERT", new FractionalSelectivity(0.5));
        application.addTupleMapping("blood_pressure_module", "BLOOD_PRESSURE", "BP_ALERT", new FractionalSelectivity(0.5));
        application.addTupleMapping("gyroscope_module", "GYROSCOPE", "GYRO_ALERT", new FractionalSelectivity(0.5));

        List<AppLoop> loops = new ArrayList<>();

        List<String> loop1 = new ArrayList<>();
        loop1.add("body_temperature_module");
        loop1.add("analyzer");
        loops.add(new AppLoop(loop1));

        List<String> loop2 = new ArrayList<>();
        loop2.add("heart_rate_module");
        loop2.add("analyzer");
        loops.add(new AppLoop(loop2));

        List<String> loop3 = new ArrayList<>();
        loop3.add("spo2_module");
        loop3.add("analyzer");
        loops.add(new AppLoop(loop3));


        List<String> loop5 = new ArrayList<>();
        loop5.add("accelerometer_module");
        loop5.add("analyzer");
        loops.add(new AppLoop(loop5));

        List<String> loop6 = new ArrayList<>();
        loop6.add("blood_pressure_module");
        loop6.add("analyzer");
        loops.add(new AppLoop(loop6));

        List<String> loop7 = new ArrayList<>();
        loop7.add("gyroscope_module");
        loop7.add("analyzer");
        loops.add(new AppLoop(loop7));

        application.setLoops(loops);

        return application;
    }
}
