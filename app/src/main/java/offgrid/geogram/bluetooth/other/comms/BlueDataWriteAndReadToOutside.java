package offgrid.geogram.bluetooth.other.comms;

import static offgrid.geogram.bluetooth.Bluecomm.timeBetweenChecks;
import static offgrid.geogram.bluetooth.Bluecomm.timeBetweenMessages;

import android.content.Context;

import offgrid.geogram.bluetooth.Bluecomm;
import offgrid.geogram.core.Log;

/**
 * Initiates a request to a specific device
 * and waits for the appropriate response.
 */
public class BlueDataWriteAndReadToOutside {

    private String macAddress = null;
    private String request = null;
    private DataCallbackTemplate callback = null;
    private BluePackage requestData = null;

    private final String TAG = "BlueRequest";
    private boolean stopProcessing = false;

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    /**
     * The command with settings that we are requesting
     * to the other device
     * @param request for example: "getProfile"
     */
    public void setRequest(DataType request) {
        this.request = request.toString();
    }

    public void setCallback(DataCallbackTemplate callback) {
        this.callback = callback;
    }

    /**
     * Send the request to the device on its own thread
     * and will update with the results using the callback
     */
    public void send(Context context) {
        // Create a new thread to run the operation
        new Thread(() -> {
            try {

                Thread.sleep(timeBetweenChecks);

                DataCallbackTemplate writeDataCallback = new DataCallbackTemplate() {
                    @Override
                    public void onDataSuccess(String data) {
                        Log.i(TAG, "Data sent: " + data);
                    }
                    @Override
                    public void onDataError(String errorMessage) {
                        Log.e(TAG, "Error sending data: " + errorMessage);
                    }
                };

                // send the message, wait for the reply
                Log.i(TAG, "Request sent: " + this.request);
                Bluecomm.getInstance(context).writeData(macAddress, request);
                // wait 2 seconds
                Thread.sleep(timeBetweenChecks);

                // start looping for results
                DataCallbackTemplate receiveDataCallback = new DataCallbackTemplate() {
                    @Override
                    public void onDataSuccess(String data) {
                        Log.i(TAG, "Data received: " + data);
                        try {
                            if(requestData == null){
                                requestData = BluePackage.createReceiver(data);
                            }else{
                                requestData.receiveParcel(data);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Invalid data: " + e.getMessage());
                            stopProcessing = true;
                            return;
                        }
                    }
                    @Override
                    public void onDataError(String errorMessage) {
                        Log.e(TAG, "Error receiving data: " + errorMessage);
                        stopProcessing = true;
                    }
                };

                // read the data for the first time
                Bluecomm bluecomm = Bluecomm.getInstance(context);
                bluecomm.getDataRead(this.macAddress, receiveDataCallback);

                // small delay necessary to wait for first message
                Thread.sleep(timeBetweenChecks);

                // request data needs to be valid
                if(requestData == null || stopProcessing){
                    Log.e(TAG, "Failed to receive the data header");
                    return;
                }

                // repeat in loop until all parcels are received
                for(int i = 0; i < requestData.getMessageParcelsTotal(); i++){
                    bluecomm.getDataRead(this.macAddress, receiveDataCallback);
                    Thread.sleep(timeBetweenMessages);
                    if(stopProcessing){
                        break;
                    }
                }

                // all done
                if(requestData.allParcelsReceivedAndValid()){
                    String data = requestData.getData();
                    callback.onDataSuccess(data);
                    Log.i(TAG, "All parcels received: " + requestData.getData());
                }else{
                    Log.e(TAG, "Failed to receive all parcels");
                    callback.onDataError("Failed to receive all parcels");
                }



            } catch (Exception e) {
                Log.e(TAG, "Error executing request: " + e.getMessage());
            }
        }).start();
    }
}
