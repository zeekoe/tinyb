import tinyb.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/* WS08 source:
https://github.com/rnlgreen/thermobeacon/blob/main/thermobeacon2.py
 */


public class HelloTinyB {
    static boolean running = true;

    static void printDevice(BluetoothDevice device) {
        System.out.print("Address = " + device.getAddress());
        System.out.print(" Name = " + device.getName());
        System.out.print(" Connected = " + device.getConnected());
        System.out.println();
    }
    /*
     * After discovery is started, new devices will be detected. We can get a list of all devices through the manager's
     * getDevices method. We can the look through the list of devices to find the device with the MAC which we provided
     * as a parameter. We continue looking until we find it, or we try 15 times (1 minutes).
     */
    static BluetoothDevice getDevice(String address) throws InterruptedException {
        BluetoothManager manager = BluetoothManager.getBluetoothManager();
        BluetoothDevice sensor = null;
        for (int i = 0; (i < 15) && running; ++i) {
            List<BluetoothDevice> list = manager.getDevices();
            if (list == null)
                return null;

            for (BluetoothDevice device : list) {
                printDevice(device);
                /*
                 * Here we check if the address matches.
                 */
                if (device.getAddress().equals(address))
                    sensor = device;
            }

            if (sensor != null) {
                return sensor;
            }
            Thread.sleep(4000);
        }
        return null;
    }

    /*
     * Our device should expose a temperature service, which has a UUID we can find out from the data sheet. The service
     * description of the SensorTag can be found here:
     * http://processors.wiki.ti.com/images/a/a8/BLE_SensorTag_GATT_Server.pdf. The service we are looking for has the
     * short UUID AA00 which we insert into the TI Base UUID: f000XXXX-0451-4000-b000-000000000000
     */
    static BluetoothGattService getService(BluetoothDevice device, String UUID) throws InterruptedException {
        System.out.println("Services exposed by device:");
        BluetoothGattService tempService = null;
        List<BluetoothGattService> bluetoothServices = null;
        do {
            bluetoothServices = device.getServices();
            if (bluetoothServices == null)
                return null;

            for (BluetoothGattService service : bluetoothServices) {
                System.out.println("UUID: " + service.getUUID());
                if (service.getUUID().equals(UUID))
                    tempService = service;
            }
            Thread.sleep(4000);
        } while (bluetoothServices.isEmpty() && running);
        return tempService;
    }

    static BluetoothGattCharacteristic getCharacteristic(BluetoothGattService service, String UUID) {
        List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
        if (characteristics == null)
            return null;

        for (BluetoothGattCharacteristic characteristic : characteristics) {
            if (characteristic.getUUID().equals(UUID))
                return characteristic;
        }
        return null;
    }

    public static byte[] writeBytes(BluetoothGattCharacteristic tx, BluetoothGattCharacteristic rx, String vals) {
        byte[] writeVal = hexStringToByteArray(vals);
        tx.writeValue(writeVal);
        return rx.readValue();
    }


    // Helper method to convert hex string to byte array
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    /*
     * This program connects to a WS08 ThermoBeacon and reads the temperature characteristic exposed by the device over
     * Bluetooth Low Energy. The parameter provided to the program should be the MAC address of the device.
     *
     * The API used in this example is based on TinyB v0.3, which only supports polling, but v0.4 will introduce a
     * simplied API for discovering devices and services.
     */
    public static void main(String[] args) throws InterruptedException {


        if (args.length < 1) {
            System.err.println("Run with <device_address> argument");
            System.exit(-1);
        }

        /*
         * To start looking of the device, we first must initialize the TinyB library. The way of interacting with the
         * library is through the BluetoothManager. There can be only one BluetoothManager at one time, and the
         * reference to it is obtained through the getBluetoothManager method.
         */
        BluetoothManager manager = BluetoothManager.getBluetoothManager();

        /*
         * The manager will try to initialize a BluetoothAdapter if any adapter is present in the system. To initialize
         * discovery we can call startDiscovery, which will put the default adapter in discovery mode.
         */
        boolean discoveryStarted = manager.startDiscovery();

        System.out.println("The discovery started: " + (discoveryStarted ? "true" : "false"));
        BluetoothDevice sensor = getDevice(args[0]);

        /*
         * After we find the device we can stop looking for other devices.
         */
        try {
            manager.stopDiscovery();
        } catch (BluetoothException e) {
            System.err.println("Discovery could not be stopped.");
        }

        if (sensor == null) {
            System.err.println("No sensor found with the provided address.");
            System.exit(-1);
        }

        System.out.print("Found device: ");
        printDevice(sensor);

        if (sensor.connect())
            System.out.println("Sensor with the provided address connected");
        else {
            System.out.println("Could not connect device.");
            System.exit(-1);
        }

        Lock lock = new ReentrantLock();
        Condition cv = lock.newCondition();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                running = false;
                lock.lock();
                try {
                    cv.signalAll();
                } finally {
                    lock.unlock();
                }

            }
        });


        BluetoothGattService tempService = getService(sensor, "0000ffe0-0000-1000-8000-00805f9b34fb");

        if (tempService == null) {
            System.err.println("This device does not have the temperature service we are looking for.");
            sensor.disconnect();
            System.exit(-1);
        }
        System.out.println("Found service " + tempService.getUUID());

        BluetoothGattCharacteristic rx = getCharacteristic(tempService, "0000fff3-0000-1000-8000-00805f9b34fb");
        BluetoothGattCharacteristic tx = getCharacteristic(tempService, "0000fff5-0000-1000-8000-00805f9b34fb");

        if (rx == null || tx == null) {
            System.err.println("Could not find the correct characteristics.");
            sensor.disconnect();
            System.exit(-1);
        }

        System.out.println("Found the temperature characteristics");

        /*
         * Turn on the Temperature Service by writing 1 in the configuration characteristic, as mentioned in the PDF
         * mentioned above. We could also modify the update interval, by writing in the period characteristic, but the
         * default 1s is good enough for our purposes.
         */

        /*
         * Each second read the value characteristic and display it in a human readable format.
         */
        while (running) {
            // Send initial command to get the number of available data points
            byte[] response = writeBytes(tx, rx, "0100000000");
            // The number of available values is stored in the second and third bytes of the response, little endian order
            int available = ((response[2] & 0xFF) << 8) | (response[1] & 0xFF);

            System.out.println("There are " + available + " available data points from this device (" + sensor.getAddress() + ")");

            try {
                // Data is returned as three pairs of temperature and humidity values
                int index = available - 1;
                // Print index for reference
                System.out.print(String.format("%04d", index) + ": ");
                // Convert index to hex, padded with leading zeroes
                String indexHex = String.format("%04x", index);
                // Reverse the byte order of the hex values
                String indexHexReversed = indexHex.substring(2) + indexHex.substring(0, 2);
                // Build the request string to be sent to the device
                String hexString = "07" + indexHexReversed + "000003";
                // Send the request and get the response
                response = writeBytes(tx, rx, hexString);
                // Print the response as text
                System.out.println(convertToText(response));
                // Convert the response to temperature and humidity readings
                System.out.println(convertToReadings(response));
            } catch (Exception e) {
                e.printStackTrace();
                // Handle exception
            }


            lock.lock();
            try {
                cv.await(1, TimeUnit.SECONDS);
            } finally {
                lock.unlock();
            }
        }
        sensor.disconnect();

    }


    // Function to convert the readings we get back into temperatures and humidities
    public static Reading convertToReadings(byte[] response) {
        List<BigDecimal> readings = new ArrayList<>();
        for (int v = 0; v < 6; v++) {
            int resultsPosition = 6 + (v * 2);
            double reading = ((response[resultsPosition + 1] & 0xFF) << 8) | (response[resultsPosition] & 0xFF);
            reading *= 0.0625;
            if (reading > 2048) {
                reading = -1 * (4096 - reading);
            }
            readings.add(BigDecimal.valueOf(reading).setScale(2, RoundingMode.HALF_UP));
        }
        System.out.println(readings.stream().map(r -> r.toString()).collect(Collectors.joining(", ")));
        return new Reading(readings.get(0), readings.get(3));
    }

    public static String convertToText(byte[] results) {
        StringBuilder hexResults = new StringBuilder();
        for (int v = 0; v < results.length; v++) {
            String hexValue = String.format("%02x", results[v]);
            hexResults.append(hexValue);
            if (v < results.length - 1) {
                hexResults.append(" ");
            }
        }
        return hexResults.toString();
    }

    record Reading(BigDecimal temperature, BigDecimal humidity) {}
}
