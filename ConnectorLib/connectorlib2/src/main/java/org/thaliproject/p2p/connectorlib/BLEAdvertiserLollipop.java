package org.thaliproject.p2p.connectorlib;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by juksilve on 20.4.2015.
 */
public class BLEAdvertiserLollipop {

    BLEAdvertiserLollipop that = this;

    interface BLEAdvertiserCallback{
        public void onAdvertisingStarted(AdvertiseSettings settingsInEffec, String error);
        public void onAdvertisingStopped(String error);
    }

    public class WriteStorage{
        private String  mDeviceAddress;
        private String mUUID;
        private boolean mIsCharacter;
        List<byte[]> byteArray;
        public WriteStorage(String address,String uuid, boolean isCharacter){
            mDeviceAddress = address;
            mUUID = uuid;
            byteArray = new ArrayList<byte[]>();
            mIsCharacter = isCharacter;
        }

        public boolean isCharacter(){return mIsCharacter;}
        public String getUUID(){return mUUID;}
        public String getDeviceAddress(){return mDeviceAddress;}
        public void clearData(){
            byteArray.clear();
        }
        public void addData(byte[] array){
            byteArray.add(array);
        }

        public byte[] getFullData(){
            byte[] retArray = null;
            int totalSize = 0;

            for(int i=0; i < byteArray.size();i++){
                totalSize = totalSize + byteArray.get(i).length;
            }

            int copuCounter = 0;
            if(totalSize > 0) {
                retArray = new byte[totalSize];
                for(int ii=0; ii < byteArray.size();ii++){
                    byte[] tmpArr = byteArray.get(ii);
                    System.arraycopy(tmpArr, 0, retArray,copuCounter,tmpArr.length);
                    copuCounter = copuCounter + tmpArr.length;
                }
            }else{
                retArray = new byte[]{};
            }
            return retArray;
        }
    }

    private List<WriteStorage> mWriteList = new ArrayList<WriteStorage>();

    private Context context = null;
    BLEAdvertiserCallback callback = null;
    private Handler mHandler = null;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGattServer mBluetoothGattServer;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private ArrayList<BluetoothGattService> mBluetoothGattServices;
    private List<ParcelUuid> serviceUuids;

    public BLEAdvertiserLollipop(Context Context, BLEAdvertiserCallback CallBack) {
        this.context = Context;
        this.callback = CallBack;
        this.mHandler = new Handler(this.context.getMainLooper());

        mBluetoothGattServices = new ArrayList<BluetoothGattService>();
        serviceUuids = new ArrayList<ParcelUuid>();
    }
    public synchronized void Start() {

        if (BLEBase.isBLESupported(this.context)) {
            mBluetoothManager = (BluetoothManager) this.context.getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager != null) {
                mBluetoothAdapter = mBluetoothManager.getAdapter();
                if (mBluetoothAdapter != null && mBluetoothAdapter.isMultipleAdvertisementSupported()) {
                    mBluetoothGattServer = mBluetoothManager.openGattServer(this.context, serverCallback);
                    mBluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();

                    synchronized (mBluetoothGattServices) {
                        for (int i = 0; i < mBluetoothGattServices.size(); i++) {
                            mBluetoothGattServer.addService(mBluetoothGattServices.get(i));
                        }
                    }

                    AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
                    AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();

                    dataBuilder.setIncludeTxPowerLevel(true);

                    for (int ii = 0; ii < serviceUuids.size(); ii++) {
                        dataBuilder.addServiceUuid(serviceUuids.get(ii));
                    }

                    settingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED);
                    settingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
                    settingsBuilder.setConnectable(true);

                    stopped = false;
                    mBluetoothLeAdvertiser.startAdvertising(settingsBuilder.build(), dataBuilder.build(),mAdvertiseCallback );
                } else {
                    Started(null, "MultipleAdvertisementSupported is NOT-Supported");
                }
            } else {
                Started(null, "Bluetooth is NOT-Supported");
            }
        } else {
            Started(null, "BLE is NOT-Supported");
        }
    }

    public synchronized void Stop() {
        BluetoothLeAdvertiser tmpAdver = mBluetoothLeAdvertiser;
        mBluetoothLeAdvertiser = null;
        if(tmpAdver != null) {
            debug_print("ADV-CB", "Call Stop advert");
            stopped = true;
            tmpAdver.stopAdvertising(mAdvertiseCallback);
        }

        synchronized (mBluetoothGattServices) {
            if (mBluetoothGattServices != null) {
                if (mBluetoothGattServer != null) {
                    for (BluetoothGattService serv : mBluetoothGattServices) {
                        mBluetoothGattServer.removeService(serv);
                    }
                }
                mBluetoothGattServices.clear();
            }
        }

        BluetoothGattServer tmpServ = mBluetoothGattServer;
        mBluetoothGattServer = null;
        if(tmpServ != null){
            tmpServ.clearServices();
            tmpServ.close();
        }

        if(serviceUuids != null){
            serviceUuids.clear();
        }

        mWriteList.clear();
    }

    public boolean addService(BluetoothGattService service) {
        boolean ret = false;
        synchronized (mBluetoothGattServices) {
            if (mBluetoothGattServices != null && serviceUuids != null && service != null && service.getUuid() != null) {
                mBluetoothGattServices.add(service);
                serviceUuids.add(new ParcelUuid(service.getUuid()));
                ret = true;
            }
        }

        return ret;
    }
    public boolean setCharacterValue(UUID uuid, byte[] value) {
        boolean ret = false;
        if (mBluetoothGattServices != null) {
            synchronized (mBluetoothGattServices) {
                for (BluetoothGattService tmpServ : mBluetoothGattServices) {
                    outerloop:
                    if (tmpServ != null) {
                        List<BluetoothGattCharacteristic> CharList = tmpServ.getCharacteristics();
                        if (CharList != null) {
                            for (BluetoothGattCharacteristic chara : CharList) {
                                if (chara != null && chara.getUuid().compareTo(uuid) == 0) {
                                    chara.setValue(value);
                                    ret = true;
                                    break outerloop;
                                }
                            }
                        }
                    }
                }
            }
        }
        return ret;
    }

    public boolean setDescriptorValue(UUID uuid, byte[] value) {
        boolean ret = false;
        if (mBluetoothGattServices != null) {
            synchronized (mBluetoothGattServices) {
                for (BluetoothGattService tmpServ : mBluetoothGattServices) {
                    outerloop:
                    if (tmpServ != null) {
                        List<BluetoothGattCharacteristic> CharList = tmpServ.getCharacteristics();
                        if (CharList != null) {
                            for (BluetoothGattCharacteristic chara : CharList) {
                                if (chara != null) {
                                    List<BluetoothGattDescriptor> DescList = chara.getDescriptors();
                                    if (DescList != null) {
                                        for (BluetoothGattDescriptor descr : DescList) {
                                            if (descr != null && descr.getUuid().compareTo(uuid) == 0) {
                                                descr.setValue(value);
                                                ret = true;
                                                break outerloop;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return ret;
    }

    private void Started(AdvertiseSettings settingsInEffec,String error){
        final AdvertiseSettings settingsInEffecTmp = settingsInEffec;
        final String errorTmp = error;

        that.mHandler.post(new Runnable() {
            @Override
            public void run() {
                if(callback != null) {
                    callback.onAdvertisingStarted(settingsInEffecTmp, errorTmp);
                }
            }
        });
    }
    private void Stopped(String error){
        final String errorTmp = error;

        that.mHandler.post(new Runnable() {
            @Override
            public void run() {
                if(callback != null) {
                    callback.onAdvertisingStopped(errorTmp);
                }
            }
        });
    }

    BluetoothGattServerCallback serverCallback = new BluetoothGattServerCallback() {


        @Override
        public void onCharacteristicReadRequest(android.bluetooth.BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);

            byte[] dataForResponse = new byte[] {};
            if (mBluetoothGattServices != null) {
                synchronized (mBluetoothGattServices) {
                    for (BluetoothGattService tmpServ : mBluetoothGattServices) {
                        outerloop:
                        if (tmpServ != null) {
                            List<BluetoothGattCharacteristic> CharList = tmpServ.getCharacteristics();
                            if (CharList != null) {
                                for (BluetoothGattCharacteristic chara : CharList) {
                                    if (chara != null && chara.getUuid().compareTo(characteristic.getUuid()) == 0) {
                                        String tmpString = chara.getStringValue(offset);
                                        if (tmpString != null && tmpString.length() > 0) {
                                            dataForResponse = tmpString.getBytes();
                                        }
                                        break outerloop;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (mBluetoothGattServer != null){
                mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, dataForResponse);
            }
        }


        @Override
        public void onDescriptorReadRequest(android.bluetooth.BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);

            byte[] dataForResponse = new byte[] {};

            if (mBluetoothGattServices != null) {
                synchronized (mBluetoothGattServices) {
                    for (BluetoothGattService tmpServ : mBluetoothGattServices) {
                        outerloop:
                        if (tmpServ != null) {
                            List<BluetoothGattCharacteristic> CharList = tmpServ.getCharacteristics();
                            if (CharList != null) {
                                for (BluetoothGattCharacteristic chara : CharList) {
                                    if (chara != null) {
                                        List<BluetoothGattDescriptor> DescList = chara.getDescriptors();
                                        if (DescList != null) {
                                            for (BluetoothGattDescriptor descr : DescList) {
                                                if (descr != null && descr.getUuid().compareTo(descriptor.getUuid()) == 0) {

                                                    byte[] tmpString = descr.getValue();

                                                    if (tmpString == null || offset > tmpString.length) {
                                                        //lets just return the empty string we have made already
                                                    } else {
                                                        dataForResponse = new byte[tmpString.length - offset];
                                                        for (int i = 0; i != (tmpString.length - offset); ++i) {
                                                            dataForResponse[i] = tmpString[offset + i];
                                                        }
                                                    }
                                                    break outerloop;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (mBluetoothGattServer != null){
                mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, dataForResponse);
            }
        }
    };



    boolean stopped = false;
    AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffec) {
            if(stopped) {
                debug_print("ADV-CB", "Stopped OK");
                Stopped(null);
            }else{
                debug_print("ADV-CB", "Started OK");
                Started(settingsInEffec, null);
            }
        }

        @Override
        public void onStartFailure(int result) {
            String errBuffer = "";
            if (result == AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE) {
                errBuffer = "Failed to start advertising as the advertise data to be broadcasted is larger than 31 bytes.";
            } else if (result == AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS) {
                errBuffer = "Failed to start advertising because no advertising instance is available.";
            } else if (result == AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED) {
                errBuffer = "Failed to start advertising as the advertising is already started.";
            } else if (result == AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR) {
                errBuffer = "Operation failed due to an internal error.";
            } else if (result == AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED) {
                errBuffer = "This feature is not supported on this platform.";
            } else {
                errBuffer = "There was unknown error(" + String.format("%02X", result) + ")";
            }

            if(stopped) {
                debug_print("ADV-CB", "Stopped OK");
                Stopped(errBuffer);
            }else{
                debug_print("ADV-CB", "Started OK");
                Started(null,errBuffer);
            }
        }
    };

    private void debug_print(String who, String what){
        Log.i(who, what);
    }
}
