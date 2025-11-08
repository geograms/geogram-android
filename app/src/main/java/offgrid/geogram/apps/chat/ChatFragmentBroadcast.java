package offgrid.geogram.apps.chat;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Iterator;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.ble.BluetoothSender;
import offgrid.geogram.core.Central;
import offgrid.geogram.core.Log;
import offgrid.geogram.database.DatabaseMessages;
import offgrid.geogram.old.databaseold.BioProfile;
import offgrid.geogram.old.bluetooth_old.broadcast.BroadcastSender;
import offgrid.geogram.util.DateUtils;

public class ChatFragmentBroadcast extends Fragment {

    public static String TAG = "BroadcastChatFragment";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout chatMessageContainer;
    private ScrollView chatScrollView;

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

        // Send button functionality
        btnSend.setOnClickListener(v -> {
            String message = messageInput.getText().toString().trim();
            if(message.isEmpty() || message.isBlank()){
                return;
            }
            new Thread(() -> {
                requireActivity().runOnUiThread(() -> {
                    // send the message
                    BluetoothSender.getInstance(getContext()).sendMessage(message);
                    // Send the message via BroadcastChat
                    messageInput.setText("");
                    // Scroll to the bottom of the chat
                    chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
                });
//                boolean success = BroadcastSender.broadcast(messageToBroadcast, getContext());
//                requireActivity().runOnUiThread(() -> {
//                    if (success) {
//                        messageInput.setText("");
//
//                        // Scroll to the bottom of the chat
//                        chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
//                    } else {
//                        Toast.makeText(getContext(), "Failed to send message. Please check Bluetooth.", Toast.LENGTH_SHORT).show();
//                    }
//                });


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
        handler.removeCallbacksAndMessages(null);
        BroadcastSender.removeMessageUpdateListener();
    }



    /**
     * Updates the chat message container with new messages.
     */
    private void updateMessages() {
        boolean newMessagesWereAdded = false;

        // get the list of tickets in reverse order (most recent first)
        Iterator<ChatMessage> messageIterator = DatabaseMessages.getInstance().getMessages().descendingIterator();
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
        TextView textBoxLower = userMessageView.findViewById(R.id.lower_text);

        long timeStamp = message.getTimestamp();
        String dateText = DateUtils.convertTimestampForChatMessage(timeStamp);
        textBoxUpper.setText("");
        textBoxLower.setText(dateText);

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
        TextView textBoxLower = receivedMessageView.findViewById(R.id.message_boxLower);

        BioProfile profile = null; //BioDatabase.get(message.getAuthorId(), this.getContext());
        String nickname = "";

        if (profile != null) {
            nickname = profile.getNick();
        }

        // Add the timestamp
        long timeStamp = message.getTimestamp();
        String dateText = DateUtils.convertTimestampForChatMessage(timeStamp);
        textBoxUpper.setText("");

        // Set the sender's name
        if (nickname.isEmpty() && message.getAuthorId() != null) {
            textBoxLower.setText(message.getAuthorId());
        } else {
            String idText = nickname + "    " + dateText;
            textBoxLower.setText(idText);
        }



        // Set the message content
        String text = message.getMessage();
//        if(text.startsWith(tagBio) && profile != null){
//            if(profile.getExtra() == null){
//                // add a nice one line ASCII emoticon
//                profile.setExtra(ASCII.getRandomOneliner());
//                BioDatabase.save(profile.getDeviceId(), profile, this.getContext());
//            }
//            text = profile.getExtra();
//        }
        TextView messageTextView = receivedMessageView.findViewById(R.id.message_user_1);
        messageTextView.setText(text);

        // Apply balloon style based on preferred background color
        String colorBackground = profile != null ? profile.getColor() : "light gray";
        applyBalloonStyle(messageTextView, colorBackground);

        // Add click listener to navigate to the user profile
        receivedMessageView.setOnClickListener(v -> {
            if (profile != null) {
                //navigateToDeviceDetails(profile);
            } else {
                Toast.makeText(getContext(), "Profile not found", Toast.LENGTH_SHORT).show();
            }
        });

        // Add the view to the container
        chatMessageContainer.addView(receivedMessageView);
        chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
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
        Log.i(TAG, "onResume");
    }

    private void eraseMessagesFromWindow(){
        // clear all messages from the view
        //messageLog.clear();

        if (chatMessageContainer != null) {
            chatMessageContainer.removeAllViews();
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


}
