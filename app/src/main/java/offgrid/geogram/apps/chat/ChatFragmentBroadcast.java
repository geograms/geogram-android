package offgrid.geogram.apps.chat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.api.GeogramChatAPI;
import offgrid.geogram.ble.BluetoothSender;
import offgrid.geogram.core.Central;
import offgrid.geogram.core.Log;
import offgrid.geogram.database.DatabaseMessages;
// Removed (legacy Google Play Services code) - import offgrid.geogram.old.databaseold.BioProfile;
// Removed (legacy Google Play Services code) - import offgrid.geogram.old.bluetooth_old.broadcast.BroadcastSender;
import offgrid.geogram.settings.SettingsLoader;
import offgrid.geogram.settings.SettingsUser;
import offgrid.geogram.util.DateUtils;

public class ChatFragmentBroadcast extends Fragment {

    public static String TAG = "BroadcastChatFragment";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout chatMessageContainer;
    private ScrollView chatScrollView;
    private Spinner spinnerCommunicationMode;
    private android.widget.SeekBar seekBarRadius;
    private android.widget.TextView tvRadiusValue;
    private String selectedCommunicationMode;
    private int selectedRadiusKm;
    private Runnable internetMessagePoller;
    private static final long POLL_INTERVAL_MS = 30000; // 30 seconds, like HTML app
    private Location lastKnownLocation;
    private static final int DEFAULT_RADIUS_KM = 100;

    // Read receipt rate limiting
    private final java.util.Set<String> sentReadReceipts = new java.util.HashSet<>();
    private long lastReadReceiptTime = 0;
    private static final long READ_RECEIPT_THROTTLE_MS = 5000; // Minimum 5 seconds between read receipts
    private static final int MAX_READ_RECEIPTS_PER_SESSION = 20; // Limit per UI refresh

    public boolean canAddMessages() {
        return isAdded()                      // Fragment is attached to Activity
                && getContext() != null       // Context is not null
                && getView() != null          // Root view is available
                && getUserVisibleHintSafe()   // Only update if user can actually see the screen
                && chatMessageContainer != null
                && chatScrollView != null;
    }

    private boolean getUserVisibleHintSafe() {
        // If you're using ViewPager, replace with isVisible() or similar
        return isVisible() && !isHidden();
    }

    public void addMessage(ChatMessage message){
        // get the id for this device
        String idThisDevice = Central.getInstance().getSettings().getIdDevice();
        if(canAddMessages()){
            // when possible, include the message on the chat window
            updateMessage(idThisDevice, message);
        }
    }

    /**
     * Refreshes the entire message list from the database.
     * This method is safe to call from any thread - it will automatically
     * switch to the UI thread if needed.
     * Used by event handlers to trigger UI updates without direct addMessage() calls.
     */
    public void refreshMessagesFromDatabase(){
        if(!canAddMessages()){
            return;
        }

        // Ensure we're on the UI thread
        if(getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if(canAddMessages()){
                    eraseMessagesFromWindow();
                    updateMessages();
                }
            });
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_broadcast_chat, container, false);

        // Initialize message input and send button
        EditText messageInput = view.findViewById(R.id.message_input);
        ImageButton btnSend = view.findViewById(R.id.btn_send);

        // Initialize chat message container and scroll view
        chatMessageContainer = view.findViewById(R.id.chat_message_container);
        chatScrollView = view.findViewById(R.id.chat_scroll_view);

        // Initialize controls
        spinnerCommunicationMode = view.findViewById(R.id.spinner_communication_mode);
        seekBarRadius = view.findViewById(R.id.seekbar_radius);
        tvRadiusValue = view.findViewById(R.id.tv_radius_value);

        // Load saved settings
        SettingsUser settings = Central.getInstance().getSettings();
        selectedCommunicationMode = settings.getChatCommunicationMode();
        selectedRadiusKm = settings.getChatRadiusKm();

        // Setup communication mode spinner
        ArrayAdapter<CharSequence> modeAdapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.communication_modes,
                R.layout.spinner_item
        );
        modeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerCommunicationMode.setAdapter(modeAdapter);

        // Set saved selection
        int modePosition = getModePosition(selectedCommunicationMode);
        spinnerCommunicationMode.setSelection(modePosition);

        spinnerCommunicationMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedCommunicationMode = parent.getItemAtPosition(position).toString();
                Log.i(TAG, "Communication mode selected: " + selectedCommunicationMode);

                // Save to settings
                SettingsUser settings = Central.getInstance().getSettings();
                settings.setChatCommunicationMode(selectedCommunicationMode);
                SettingsLoader.saveSettings(requireContext(), settings);

                // Start/stop internet polling based on mode
                handleCommunicationModeChange();

                // Refresh messages immediately
                eraseMessagesFromWindow();
                updateMessages();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        // Setup radius SeekBar (1-200 km range)
        seekBarRadius.setMax(200);
        seekBarRadius.setMin(1);
        seekBarRadius.setProgress(selectedRadiusKm);
        tvRadiusValue.setText(selectedRadiusKm + " km");

        seekBarRadius.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                // Ensure minimum is 1 km
                if (progress < 1) {
                    progress = 1;
                    seekBar.setProgress(1);
                }
                selectedRadiusKm = progress;
                tvRadiusValue.setText(progress + " km");
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {
                // Not needed
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
                // Save to settings when user releases the slider
                Log.i(TAG, "Radius selected: " + selectedRadiusKm + " km");
                SettingsUser settings = Central.getInstance().getSettings();
                settings.setChatRadiusKm(selectedRadiusKm);
                SettingsLoader.saveSettings(requireContext(), settings);

                // Refresh messages with new radius
                if (shouldFetchInternetMessages()) {
                    eraseMessagesFromWindow();
                    updateMessages();
                }
            }
        });

        // Send button functionality
        btnSend.setOnClickListener(v -> {
            String message = messageInput.getText().toString().trim();
            if(message.isEmpty() || message.isBlank()){
                return;
            }

            // Clear input
            messageInput.setText("");

            // Send in background
            // Note: Message will be added to database and UI by event handlers
            // (EventBleBroadcastMessageSent for local, or after API success for internet)
            new Thread(() -> {
                boolean sentWiFi = false;
                boolean sentBLE = false;
                boolean sentInternet = false;
                String errorMessage = null;
                int wifiDeviceCount = 0;

                // Create a single message that we'll add channels to
                ChatMessage combinedMessage = new ChatMessage(
                    Central.getInstance().getSettings().getCallsign(),
                    message
                );
                combinedMessage.setWrittenByMe(true);
                combinedMessage.setTimestamp(System.currentTimeMillis());
                combinedMessage.setDestinationId("ANY");

                // Send via WiFi and/or Bluetooth if needed (LOCAL communication)
                if ("Local only".equals(selectedCommunicationMode) || "Everything".equals(selectedCommunicationMode)) {
                    try {
                        // PRIORITY 1: Send via WiFi to discovered LAN devices
                        offgrid.geogram.wifi.WiFiMessageSender wifiSender =
                            offgrid.geogram.wifi.WiFiMessageSender.getInstance(getContext());

                        wifiDeviceCount = wifiSender.getWiFiDeviceCount();

                        if (wifiDeviceCount > 0) {
                            // WiFi devices available - send ONLY via WiFi, never via BLE
                            // This prevents duplicate messages on receivers
                            Log.i(TAG, "WiFi devices available (" + wifiDeviceCount + ") - sending via WiFi ONLY (no BLE)");
                            wifiSender.sendBroadcastMessage(message);

                            // Mark as sent via WiFi (sends happen asynchronously in background)
                            sentWiFi = true;
                            combinedMessage.addChannel(ChatMessageType.WIFI);
                            Log.i(TAG, "✓ WiFi message queued for " + wifiDeviceCount + " devices");

                        } else {
                            // NO WiFi devices - send via BLE instead
                            Log.i(TAG, "No WiFi devices available - sending via BLE");
                            BluetoothSender.getInstance(getContext()).sendMessage(message);
                            sentBLE = true;
                            combinedMessage.addChannel(ChatMessageType.LOCAL);
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Failed to send local message", e);
                        errorMessage = "Local send failed: " + e.getMessage();
                    }
                }

                // Send via Internet if needed
                if ("Internet only".equals(selectedCommunicationMode) || "Everything".equals(selectedCommunicationMode)) {
                    try {
                        Location location = getLastKnownLocation();
                        if (location != null) {
                            SettingsUser userSettings = Central.getInstance().getSettings();
                            String userCallsign = userSettings.getCallsign();
                            String nsec = userSettings.getNsec();
                            String npub = userSettings.getNpub();

                            Log.d(TAG, "Sending internet message - Location: " + location.getLatitude() + "," + location.getLongitude());
                            Log.d(TAG, "Callsign: " + userCallsign);

                            if (userCallsign != null && !userCallsign.isEmpty() && nsec != null && !nsec.isEmpty() && npub != null && !npub.isEmpty()) {
                                boolean success = GeogramChatAPI.writeMessage(
                                        location.getLatitude(),
                                        location.getLongitude(),
                                        message,
                                        userCallsign,
                                        nsec,
                                        npub
                                );

                                if (success) {
                                    sentInternet = true;
                                    combinedMessage.addChannel(ChatMessageType.INTERNET);
                                    Log.i(TAG, "Message sent to server successfully");
                                } else {
                                    Log.e(TAG, "Server returned failure response");
                                    errorMessage = "Server rejected message";
                                }
                            } else {
                                Log.e(TAG, "User identity not configured");
                                errorMessage = "User identity not configured (callsign, nsec, or npub missing)";
                            }
                        } else {
                            Log.e(TAG, "No location available for internet send");
                            errorMessage = "No location available";
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to send via Internet", e);
                        errorMessage = "Internet send failed: " + e.getMessage();
                    }
                }

                // Save the combined message ONCE with all channels
                // NOTE: If BLE was used, EventBleBroadcastMessageSent already saved it
                if (sentBLE && sentInternet) {
                    // BLE + Internet: BLE event handler saved the message, add INTERNET channel to it
                    // Wait a moment for the BLE event handler to save the message
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        // Ignore
                    }

                    // Find the message that was just saved by the BLE event handler and add INTERNET channel
                    ChatMessage savedMessage = findRecentMessage(message, 5000);
                    if (savedMessage != null) {
                        savedMessage.addChannel(ChatMessageType.INTERNET);
                        DatabaseMessages.getInstance().flushNow();
                        Log.i(TAG, "Added INTERNET channel to BLE message");
                    } else {
                        Log.w(TAG, "Could not find BLE message to add INTERNET channel");
                    }

                    // Refresh UI
                    requireActivity().runOnUiThread(() -> {
                        refreshMessagesFromDatabase();
                    });

                } else if (sentWiFi || sentInternet) {
                    // WiFi and/or Internet only (no BLE): Save our combined message
                    if (sentWiFi) {
                        combinedMessage.setMessageType(ChatMessageType.WIFI);
                    } else if (sentInternet) {
                        combinedMessage.setMessageType(ChatMessageType.INTERNET);
                    }

                    DatabaseMessages.getInstance().add(combinedMessage);
                    DatabaseMessages.getInstance().flushNow();

                    // Refresh UI to show message immediately
                    requireActivity().runOnUiThread(() -> {
                        refreshMessagesFromDatabase();
                    });
                }
                // If only BLE was sent, the event handler already saved it, so do nothing

                final boolean anySent = sentWiFi || sentBLE || sentInternet;
                final boolean finalSentWiFi = sentWiFi;
                final boolean finalSentBLE = sentBLE;
                final boolean finalSentInternet = sentInternet;
                final int finalWiFiCount = wifiDeviceCount;
                final String finalError = errorMessage;

                requireActivity().runOnUiThread(() -> {
                    // Show success/error message only - UI refresh is handled by event handlers
                    if (anySent) {
                        StringBuilder statusMsg = new StringBuilder("Message sent via: ");
                        boolean first = true;

                        if (finalSentWiFi) {
                            statusMsg.append("WiFi (").append(finalWiFiCount).append(" devices)");
                            first = false;
                        }
                        if (finalSentBLE) {
                            if (!first) statusMsg.append(", ");
                            statusMsg.append("BLE");
                            first = false;
                        }
                        if (finalSentInternet) {
                            if (!first) statusMsg.append(", ");
                            statusMsg.append("Internet");
                        }

                        Toast.makeText(getContext(), statusMsg.toString(), Toast.LENGTH_SHORT).show();
                    } else if (finalError != null) {
                        // Send failed - show error
                        Toast.makeText(getContext(), finalError, Toast.LENGTH_LONG).show();
                    }
                });
            }).start();
        });


        // update the message right now on the chat box
        this.eraseMessagesFromWindow();
        // add all messages again
        updateMessages();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Stop message polling and unregister listener to avoid memory leaks
        stopInternetMessagePolling();
        handler.removeCallbacksAndMessages(null);
        // Clear read receipt tracking to prevent memory leaks
        sentReadReceipts.clear();
        // Removed (legacy) - BroadcastSender.removeMessageUpdateListener();
    }



    /**
     * Updates the chat message container with new messages.
     */
    private void updateMessages() {
        boolean newMessagesWereAdded = false;

        // Create a snapshot copy to avoid ConcurrentModificationException
        // when background service adds messages while we iterate
        TreeSet<ChatMessage> messagesCopy;
        synchronized (DatabaseMessages.getInstance()) {
            messagesCopy = new TreeSet<>(DatabaseMessages.getInstance().getMessages());
        }

        // get the list of tickets in reverse order (most recent first)
        Iterator<ChatMessage> messageIterator = messagesCopy.descendingIterator();
        if(messageIterator.hasNext() == false){
            return;
        }

        String idThisDevice = Central.getInstance().getSettings().getIdDevice();

        // iterate all messages, starting with the most recent ones
        while(messageIterator.hasNext()){
            ChatMessage message = messageIterator.next();
            updateMessage(idThisDevice, message);
//            // message types to ignore
//            // ignore position messages that start with +
//            if(message.getMessage().startsWith("+")){
//                continue;
//            }
//
//            // display the message on the screen
//            if (message.getAuthorId().equals(idThisDevice)
//                    //message.isWrittenByMe()
//            ) {
//                addUserMessage(message);
//            } else {
//                addReceivedMessage(message);
//            }
//            // inform that new messages were added
//            newMessagesWereAdded = true;
        }
       // if(newMessagesWereAdded){
            chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
        //}
    }

    private void updateMessage(String idThisDevice, ChatMessage message) {
        // message types to ignore
        // ignore position messages that start with +
        if(message.getMessage().startsWith("+")){
            return;
        }

        // Filter messages based on communication mode
        if (!shouldDisplayMessage(message)) {
            return;
        }

        // display the message on the screen
        if (message.getAuthorId().equals(idThisDevice)
            //message.isWrittenByMe()
        ) {
            addUserMessage(message);
        } else {
            addReceivedMessage(message);
        }
    }

    /**
     * Check if message should be displayed based on communication mode
     */
    private boolean shouldDisplayMessage(ChatMessage message) {
        // First check: Only show broadcast/global messages in this fragment
        // Broadcast messages have destinationId == null or "ANY"
        // Direct/group messages have specific device IDs or group identifiers
        String destination = message.destinationId;
        if (destination != null && !destination.equals("ANY")) {
            // This is a direct or group message, not a broadcast - don't show it here
            return false;
        }

        if (selectedCommunicationMode == null) {
            return true; // Show all if mode not set
        }

        // Check channels set for multi-channel support
        boolean hasLocal = message.hasChannel(ChatMessageType.LOCAL);
        boolean hasInternet = message.hasChannel(ChatMessageType.INTERNET);

        // Fallback for legacy messages without channels
        if (!hasLocal && !hasInternet) {
            ChatMessageType type = message.getMessageType();
            if (type == ChatMessageType.LOCAL) {
                hasLocal = true;
            } else if (type == ChatMessageType.INTERNET) {
                hasInternet = true;
            } else {
                // Old messages without a type set should be treated as LOCAL
                hasLocal = true;
            }
        }

        switch (selectedCommunicationMode) {
            case "Local only":
                return hasLocal;
            case "Internet only":
                return hasInternet;
            case "Everything":
                return hasLocal || hasInternet;
            default:
                return true; // Show all by default
        }
    }

    /**
     * Adds a user message to the chat message container.
     *
     * @param message The message to display.
     */
    private void addUserMessage(ChatMessage message) {
        View userMessageView = LayoutInflater.from(getContext())
                .inflate(R.layout.item_user_message, chatMessageContainer, false);
        TextView messageTextView = userMessageView.findViewById(R.id.message_user_self);
        messageTextView.setText(message.getMessage());

        // add the other details
        TextView textBoxUpper = userMessageView.findViewById(R.id.upper_text);

        long timeStamp = message.getTimestamp();
        String dateText = DateUtils.convertTimestampForChatMessage(timeStamp);
        // Show only timestamp in upper text
        textBoxUpper.setText(dateText);

        // Add channel indicators
        LinearLayout channelIndicators = userMessageView.findViewById(R.id.channel_indicators);
        addChannelIndicators(channelIndicators, message);

        try{
            chatMessageContainer.addView(userMessageView);
            chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
        }catch (Exception e){
            Log.e(TAG, "Failed to add message on the chat window");
        }
    }

    /**
     * Adds a received message to the chat message container.
     *
     * @param message The message to display.
     */
    private void addReceivedMessage(ChatMessage message) {
        View receivedMessageView = LayoutInflater.from(getContext())
                .inflate(R.layout.item_received_message, chatMessageContainer, false);

        // Get the objects
        TextView textBoxUpper = receivedMessageView.findViewById(R.id.message_boxUpper);

        // Removed (legacy) - BioProfile was part of old Google Play Services code
        // BioProfile profile = null; //BioDatabase.get(message.getAuthorId(), this.getContext());
        String nickname = message.getAuthorId(); // Use author ID as nickname

        // Add the timestamp
        long timeStamp = message.getTimestamp();
        String dateText = DateUtils.convertTimestampForChatMessage(timeStamp);

        // Set the sender's name and timestamp in upper text
        String idText = nickname + " - " + dateText;
        textBoxUpper.setText(idText);

        // Add channel indicators
        LinearLayout channelIndicators = receivedMessageView.findViewById(R.id.channel_indicators);
        addChannelIndicators(channelIndicators, message);

        // Set the message content
        String text = message.getMessage();
        TextView messageTextView = receivedMessageView.findViewById(R.id.message_user_1);
        messageTextView.setText(text);

        // Apply balloon style based on user's unique color
        String colorBackground = getUserColor(message.getAuthorId());
        applyBalloonStyle(messageTextView, colorBackground);

        // Add click listener to open device profile
        receivedMessageView.setOnClickListener(v -> {
            openDeviceProfile(message.getAuthorId());
        });

        // Add the view to the container
        chatMessageContainer.addView(receivedMessageView);
        chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));

        // Send READ receipt for messages received via Bluetooth
        sendReadReceiptIfNeeded(message);
    }

    /**
     * Add channel indicator badges to show which channels the message was sent through
     * @param container The LinearLayout to add indicators to
     * @param message The message to check channels for
     */
    private void addChannelIndicators(LinearLayout container, ChatMessage message) {
        container.removeAllViews(); // Clear any existing indicators

        boolean hasLocal = message.hasChannel(ChatMessageType.LOCAL);
        boolean hasInternet = message.hasChannel(ChatMessageType.INTERNET);

        // Fallback for legacy messages without channels set
        if (!hasLocal && !hasInternet) {
            ChatMessageType type = message.getMessageType();
            if (type == ChatMessageType.LOCAL) {
                hasLocal = true;
            } else if (type == ChatMessageType.INTERNET) {
                hasInternet = true;
            }
        }

        // Add Bluetooth indicator
        if (hasLocal) {
            TextView bleIndicator = new TextView(getContext());
            bleIndicator.setText("BLE");
            bleIndicator.setTextSize(10);
            bleIndicator.setTextColor(getResources().getColor(android.R.color.white));
            bleIndicator.setBackgroundColor(0xFF666666); // Dark grey background
            bleIndicator.setPadding(8, 4, 8, 4);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 8, 0);
            bleIndicator.setLayoutParams(params);
            container.addView(bleIndicator);

            // Add read count badge for messages sent by me (only for broadcast messages)
            android.util.Log.d("ReadReceipts", "Badge check - isWrittenByMe: " + message.isWrittenByMe() +
                    ", readCount: " + message.getReadCount() +
                    ", msg: " + message.getMessage().substring(0, Math.min(20, message.getMessage().length())));

            if (message.isWrittenByMe() && message.getReadCount() > 0) {
                android.util.Log.i("ReadReceipts", "SHOWING READ BADGE - count: " + message.getReadCount());

                TextView readCountBadge = new TextView(getContext());
                readCountBadge.setText("\uD83D\uDC41 " + message.getReadCount()); // Eye emoji + count
                readCountBadge.setTextSize(10);
                readCountBadge.setTextColor(getResources().getColor(android.R.color.white));
                readCountBadge.setBackgroundColor(0xFF4A90E2); // Blue background to indicate interactivity
                readCountBadge.setPadding(8, 4, 8, 4);
                LinearLayout.LayoutParams readCountParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                readCountParams.setMargins(0, 0, 8, 0);
                readCountBadge.setLayoutParams(readCountParams);

                // Make it clickable to show reader list
                readCountBadge.setOnClickListener(v -> showReadReceiptDialog(message));

                container.addView(readCountBadge);
            } else if (message.isWrittenByMe()) {
                android.util.Log.d("ReadReceipts", "My message but no reads yet");
            }
        }

        // Add Internet indicator
        if (hasInternet) {
            TextView internetIndicator = new TextView(getContext());
            internetIndicator.setText("NET");
            internetIndicator.setTextSize(10);
            internetIndicator.setTextColor(getResources().getColor(android.R.color.white));
            internetIndicator.setBackgroundColor(0xFF666666); // Dark grey background
            internetIndicator.setPadding(8, 4, 8, 4);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 8, 0);
            internetIndicator.setLayoutParams(params);
            container.addView(internetIndicator);
        }

        // Check for WiFi channel
        boolean hasWifi = message.hasChannel(ChatMessageType.WIFI);
        if (!hasWifi && message.getMessageType() == ChatMessageType.WIFI) {
            hasWifi = true;
        }

        // Add WiFi indicator
        if (hasWifi) {
            TextView wifiIndicator = new TextView(getContext());
            wifiIndicator.setText("WIFI");
            wifiIndicator.setTextSize(10);
            wifiIndicator.setTextColor(getResources().getColor(android.R.color.white));
            wifiIndicator.setBackgroundColor(0xFF666666); // Dark grey background
            wifiIndicator.setPadding(8, 4, 8, 4);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 8, 0);
            wifiIndicator.setLayoutParams(params);
            container.addView(wifiIndicator);
        }
    }

    /**
     * Generate a consistent color for each user based on their authorId
     * @param authorId The user's callsign/ID
     * @return A color name for the balloon background
     */
    private String getUserColor(String authorId) {
        if (authorId == null || authorId.isEmpty()) {
            return "light gray";
        }

        // Try to get preferred color from device profile if available
        java.util.TreeSet<offgrid.geogram.devices.Device> devices =
            offgrid.geogram.devices.DeviceManager.getInstance().getDevicesSpotted();

        for (offgrid.geogram.devices.Device device : devices) {
            if (device.ID.equals(authorId)) {
                String preferredColor = device.getProfilePreferredColor();
                if (preferredColor != null && !preferredColor.isEmpty()) {
                    return preferredColor;
                }
                break;
            }
        }

        // Fallback: Array of pleasant, distinguishable colors (avoiding red/yellow which look like warnings)
        String[] colors = {
            "light blue",
            "light green",
            "light cyan",
            "pink",
            "cyan",
            "magenta",
            "blue",
            "green",
            "brown",
            "dark gray"
        };

        // Generate consistent hash from authorId
        int hash = Math.abs(authorId.hashCode());
        int colorIndex = hash % colors.length;

        return colors[colorIndex];
    }

    @Override
    public void onResume() {
        super.onResume();

        // Show top action bar for main screens
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTopActionBarVisible(true);
        }

        // Hide the floating action button
//        FloatingActionButton btnAdd = requireActivity().findViewById(R.id.btn_add);
//        if (btnAdd != null) {
//            btnAdd.hide();
//        }

        // clear the messages to refresh the window
        eraseMessagesFromWindow();

        // update the messages, ignoring the already written ones
        updateMessages();

        // Mark all messages as read when user opens chat
        markAllMessagesAsRead();

        // Clear Android notification
        ChatNotificationManager.getInstance(getContext()).clearNotification();

        // Update the unread message counter (should now be 0)
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateChatCount();
        }

        // Start internet polling if needed
        handleCommunicationModeChange();

        Log.i(TAG, "onResume");
    }

    private void eraseMessagesFromWindow(){
        // clear all messages from the view
        //messageLog.clear();

        if (chatMessageContainer != null) {
            chatMessageContainer.removeAllViews();
        }
    }

    /**
     * Mark all received messages as read when user opens the chat.
     * This resets the unread message counter.
     */
    private void markAllMessagesAsRead() {
        try {
            boolean hasChanges = false;
            for (ChatMessage message : DatabaseMessages.getInstance().getMessages()) {
                // Only mark messages we received (not written by us) as read
                if (!message.isWrittenByMe() && !message.isRead()) {
                    message.setRead(true);
                    hasChanges = true;
                }
            }

            // Save changes to database if any messages were marked as read
            if (hasChanges) {
                DatabaseMessages.getInstance().flushNow();
                Log.d(TAG, "Marked all received messages as read");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error marking messages as read: " + e.getMessage());
        }
    }


//    private void navigateToDeviceDetails(BioProfile profile) {
//        DeviceDetailsFragment fragment = DeviceDetailsFragment.newInstance(profile);
//
//        // make the screen appear
//        MainActivity.activity.getSupportFragmentManager()
//                .beginTransaction()
//                .replace(R.id.main, DeviceDetailsFragment.newInstance(profile))
//                .addToBackStack(null)
//                .commit();
//
//    }


    /**
     * Handle communication mode changes
     */
    private void handleCommunicationModeChange() {
        // Stop any existing polling
        stopInternetMessagePolling();

        // Start polling if needed
        if (shouldFetchInternetMessages()) {
            startInternetMessagePolling();
        }
    }

    /**
     * Check if we should fetch messages from the internet
     */
    private boolean shouldFetchInternetMessages() {
        return "Internet only".equals(selectedCommunicationMode)
                || "Everything".equals(selectedCommunicationMode);
    }

    /**
     * Start polling for internet messages
     */
    private void startInternetMessagePolling() {
        if (internetMessagePoller != null) {
            return; // Already running
        }

        internetMessagePoller = new Runnable() {
            @Override
            public void run() {
                fetchInternetMessages();
                handler.postDelayed(this, POLL_INTERVAL_MS);
            }
        };

        // Fetch immediately, then schedule periodic fetches
        handler.post(internetMessagePoller);
        Log.i(TAG, "Started internet message polling");
    }

    /**
     * Stop polling for internet messages
     */
    private void stopInternetMessagePolling() {
        if (internetMessagePoller != null) {
            handler.removeCallbacks(internetMessagePoller);
            internetMessagePoller = null;
            Log.i(TAG, "Stopped internet message polling");
        }
    }

    /**
     * Fetch messages from the internet API
     */
    private void fetchInternetMessages() {
        new Thread(() -> {
            try {
                // Get location
                Location location = getLastKnownLocation();
                if (location == null) {
                    Log.i(TAG, "No location available for internet messages");
                    return;
                }

                // Get user settings
                SettingsUser settings = Central.getInstance().getSettings();
                String callsign = settings.getCallsign();
                String nsec = settings.getNsec();
                String npub = settings.getNpub();

                if (callsign == null || nsec == null || npub == null) {
                    Log.i(TAG, "User identity not configured");
                    return;
                }

                // Fetch messages from API
                int radiusKm = getSelectedRadiusKm();
                List<ChatMessage> messages = GeogramChatAPI.readMessages(
                        location.getLatitude(),
                        location.getLongitude(),
                        radiusKm,
                        callsign,
                        nsec,
                        npub
                );

                // Add messages to database (check for duplicates first)
                int newMessagesAdded = 0;
                for (ChatMessage message : messages) {
                    // Check if this message already exists in database
                    if (!messageExists(message)) {
                        DatabaseMessages.getInstance().add(message);
                        newMessagesAdded++;
                    } else {
                        Log.d(TAG, "Skipping duplicate message: " + message.getMessage().substring(0, Math.min(30, message.getMessage().length())));
                    }
                }

                // Update UI on main thread only if new messages were added
                if (newMessagesAdded > 0) {
                    Log.i(TAG, "Fetched " + newMessagesAdded + " new messages from internet");
                    requireActivity().runOnUiThread(() -> {
                        eraseMessagesFromWindow();
                        updateMessages();
                    });
                } else {
                    Log.d(TAG, "No new messages from internet");
                }

                Log.i(TAG, "Fetched " + messages.size() + " messages from internet");

            } catch (Exception e) {
                Log.e(TAG, "Error fetching internet messages", e);
                // Toast removed - silent fail when no internet available
            }
        }).start();
    }

    /**
     * Get the last known location
     */
    private Location getLastKnownLocation() {
        if (lastKnownLocation != null) {
            return lastKnownLocation;
        }

        if (getContext() == null) {
            return null;
        }

        LocationManager locationManager = (LocationManager) requireContext()
                .getSystemService(android.content.Context.LOCATION_SERVICE);

        if (locationManager == null) {
            return null;
        }

        // Check permissions
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Location permission not granted");
            return null;
        }

        // Try GPS first
        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (location == null) {
            // Fallback to network provider
            location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }

        lastKnownLocation = location;
        return location;
    }

    /**
     * Find a recently saved message by content and timestamp
     * Used to locate the message saved by BLE event handler
     */
    private ChatMessage findRecentMessage(String messageText, long maxAgeMs) {
        long now = System.currentTimeMillis();
        String myCallsign = Central.getInstance().getSettings().getCallsign();

        for (ChatMessage msg : DatabaseMessages.getInstance().getMessages()) {
            if (msg.isWrittenByMe() &&
                msg.getMessage().equals(messageText) &&
                msg.authorId.equals(myCallsign) &&
                (now - msg.getTimestamp()) < maxAgeMs) {
                return msg;
            }
        }
        return null;
    }

    /**
     * Check if a message already exists in the database
     * Compares author, message content, and timestamp (within 60 seconds tolerance)
     */
    private boolean messageExists(ChatMessage newMessage) {
        for (ChatMessage existingMsg : DatabaseMessages.getInstance().getMessages()) {
            // Same author and message content
            if (existingMsg.authorId.equals(newMessage.authorId) &&
                existingMsg.getMessage().equals(newMessage.getMessage())) {

                // Check if timestamps are close (within 60 seconds)
                // This accounts for slight time differences between devices/servers
                long timeDiff = Math.abs(existingMsg.getTimestamp() - newMessage.getTimestamp());
                if (timeDiff < 60000) { // 60 seconds tolerance
                    return true; // Duplicate found
                }
            }
        }
        return false; // Not a duplicate
    }

    /**
     * Get the currently selected communication mode
     * @return "Local only", "Internet only", or "Everything"
     */
    public String getSelectedCommunicationMode() {
        return selectedCommunicationMode;
    }

    /**
     * Get the currently selected radius
     * @return radius string (e.g., "10 km")
     */
    public String getSelectedRadius() {
        return selectedRadiusKm + " km";
    }

    /**
     * Get the radius value in kilometers
     * @return radius value as integer
     */
    public int getSelectedRadiusKm() {
        return selectedRadiusKm;
    }

    /**
     * Get spinner position for communication mode
     */
    private int getModePosition(String mode) {
        if ("Local only".equals(mode)) return 0;
        if ("Internet only".equals(mode)) return 1;
        return 2; // "Everything" is default
    }

    private void applyBalloonStyle(TextView messageTextView, String backgroundColor) {
        int bgColor;
        int textColor;

        // Define readable text color based on the background color
        switch (backgroundColor.toLowerCase()) {
            case "black":
                bgColor = getResources().getColor(R.color.black);
                textColor = getResources().getColor(R.color.white);
                break;
            case "yellow":
                bgColor = getResources().getColor(R.color.yellow);
                textColor = getResources().getColor(R.color.black);
                break;
            case "blue":
            case "green":
            case "cyan":
            case "red":
            case "magenta":
            case "pink":
            case "brown":
            case "dark gray":
            case "light red":
            case "white":
                bgColor = getResources().getColor(getResources().getIdentifier(backgroundColor.replace(" ", "_").toLowerCase(), "color", requireContext().getPackageName()));
                textColor = backgroundColor.equalsIgnoreCase("white") ? getResources().getColor(R.color.black) : getResources().getColor(R.color.white);
                break;
            case "light blue":
            case "light green":
            case "light cyan":
                bgColor = getResources().getColor(getResources().getIdentifier(backgroundColor.replace(" ", "_").toLowerCase(), "color", requireContext().getPackageName()));
                textColor = getResources().getColor(R.color.black);
                break;

            default:
                // Fallback to a neutral background and readable text color
                bgColor = getResources().getColor(R.color.light_gray); // Define a light gray fallback in colors.xml
                textColor = getResources().getColor(R.color.black);
                break;
        }

        // Apply the background and text colors to the message TextView
        if (messageTextView.getBackground() instanceof GradientDrawable) {
            GradientDrawable background = (GradientDrawable) messageTextView.getBackground();
            background.setColor(bgColor);
        }
        messageTextView.setTextColor(textColor);
    }

    /**
     * Open the device profile fragment for a given device ID
     * @param deviceId The device ID/callsign to show profile for
     */
    private void openDeviceProfile(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) {
            return;
        }

        if (getActivity() == null) {
            return;
        }

        offgrid.geogram.fragments.DeviceProfileFragment fragment =
                offgrid.geogram.fragments.DeviceProfileFragment.newInstance(deviceId);

        getActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    /**
     * Send a READ receipt for a message if it was received via Bluetooth and is from someone else
     * Implements rate limiting to prevent BLE channel flooding
     * @param message The message that was displayed
     */
    private void sendReadReceiptIfNeeded(ChatMessage message) {
        try {
            // Only send READ receipts for messages received via Bluetooth (LOCAL channel)
            if (!message.hasChannel(ChatMessageType.LOCAL)) {
                return;
            }

            // Don't send READ receipts for our own messages
            if (message.isWrittenByMe()) {
                return;
            }

            // Don't send READ receipts if we don't have a local callsign
            if (Central.getInstance() == null || Central.getInstance().getSettings() == null) {
                return;
            }

            String localCallsign = Central.getInstance().getSettings().getCallsign();
            if (localCallsign == null || localCallsign.isEmpty()) {
                return;
            }

            // Create unique key for this read receipt
            String receiptKey = message.timestamp + ":" + message.authorId;

            // Check if we already sent a read receipt for this message
            if (sentReadReceipts.contains(receiptKey)) {
                android.util.Log.d("ReadReceipts", "Already sent read receipt for message: " + receiptKey);
                return;
            }

            // Check rate limiting - enforce minimum time between read receipts
            long now = System.currentTimeMillis();
            long timeSinceLastReceipt = now - lastReadReceiptTime;

            if (timeSinceLastReceipt < READ_RECEIPT_THROTTLE_MS) {
                android.util.Log.d("ReadReceipts", "Throttled read receipt (too soon): " + timeSinceLastReceipt + "ms < " + READ_RECEIPT_THROTTLE_MS + "ms");
                return;
            }

            // Check if we've sent too many read receipts in this session
            if (sentReadReceipts.size() >= MAX_READ_RECEIPTS_PER_SESSION) {
                android.util.Log.d("ReadReceipts", "Max read receipts reached for this session (" + MAX_READ_RECEIPTS_PER_SESSION + ")");
                return;
            }

            // Send compact READ receipt: /R LAST9DIGITS AUTHOR_ID
            // Format fits in BLE advertising (20 bytes): /R 949441328 X1ADK0
            // Last 9 digits covers ~277 hours (11.5 days) of unique timestamps
            String timestampStr = String.valueOf(message.timestamp);
            String compactTimestamp = timestampStr.length() > 9 ?
                    timestampStr.substring(timestampStr.length() - 9) : timestampStr;
            String readReceipt = "/R " + compactTimestamp + " " + message.authorId;

            // Mark as sent immediately to prevent duplicates
            sentReadReceipts.add(receiptKey);
            lastReadReceiptTime = now;

            // Delay read receipt by 20 seconds to prevent queue flooding
            // User chat messages take priority over low-priority read receipts
            handler.postDelayed(() -> {
                offgrid.geogram.ble.BluetoothSender.getInstance(getContext()).sendMessage(readReceipt);
                android.util.Log.i("ReadReceipts", "SENT compact READ receipt (after 20s delay): " + readReceipt +
                        " (full timestamp: " + message.timestamp + ")");
            }, 20000); // 20 seconds

            android.util.Log.i("ReadReceipts", "QUEUED compact READ receipt for 20s delay: " + readReceipt +
                    " (full timestamp: " + message.timestamp + ")");

        } catch (Exception e) {
            android.util.Log.e("ReadReceipts", "Failed to send READ receipt: " + e.getMessage(), e);
        }
    }

    /**
     * Show a dialog with the list of people who read the message
     * @param message The message to show read receipts for
     */
    private void showReadReceiptDialog(ChatMessage message) {
        if (getContext() == null) {
            return;
        }

        // Sort read receipts by timestamp (oldest first)
        java.util.List<ReadReceipt> receipts = new java.util.ArrayList<>(message.readReceipts);
        java.util.Collections.sort(receipts);

        // Build the message list
        StringBuilder messageBuilder = new StringBuilder();
        if (receipts.isEmpty()) {
            messageBuilder.append("No one has read this message yet.");
        } else {
            messageBuilder.append("Read by ").append(receipts.size()).append(" people:\n\n");
            for (ReadReceipt receipt : receipts) {
                String dateText = offgrid.geogram.util.DateUtils.convertTimestampForChatMessage(receipt.timestamp);
                messageBuilder.append("\u2022 ").append(receipt.callsign)
                        .append("\n  ").append(dateText).append("\n\n");
            }
        }

        // Show dialog
        new android.app.AlertDialog.Builder(getContext())
                .setTitle("Read Receipts")
                .setMessage(messageBuilder.toString())
                .setPositiveButton("OK", null)
                .show();
    }


}
