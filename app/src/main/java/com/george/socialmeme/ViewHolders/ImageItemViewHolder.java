package com.george.socialmeme.ViewHolders;

import static android.content.Context.MODE_PRIVATE;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ablanco.zoomy.Zoomy;
import com.bumptech.glide.Glide;
import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;
import com.george.socialmeme.Activities.HomeActivity;
import com.george.socialmeme.Activities.UserProfileActivity;
import com.george.socialmeme.Adapters.CommentsRecyclerAdapter;
import com.george.socialmeme.BuildConfig;
import com.george.socialmeme.Models.CommentModel;
import com.george.socialmeme.R;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import de.hdodenhof.circleimageview.CircleImageView;
import maes.tech.intentanim.CustomIntent;

public class ImageItemViewHolder extends RecyclerView.ViewHolder {

    public Activity activity;
    public Context context;
    public CardView container;
    public String postID, postImageURL, postAuthorID;
    public TextView username, like_counter_tv, commentsCount, followBtn;
    public ImageView postImg;
    public ImageButton like_btn, show_comments_btn, showPostOptionsButton, shareBtn;
    public ProgressBar loadingProgressBar;
    public CircleImageView profileImage;
    public boolean isPostLiked;
    public ConstraintLayout openUserProfileView, followBtnView;
    View openCommentsView;

    DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
    DatabaseReference postsRef = FirebaseDatabase.getInstance().getReference("posts");
    DatabaseReference likesRef = FirebaseDatabase.getInstance().getReference("likes");

    FirebaseAuth auth = FirebaseAuth.getInstance();
    FirebaseUser user = auth.getCurrentUser();

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void copyUsernameToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("username", username.getText().toString());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(context, "Username copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    public void updateLikesToDB(String postID, boolean likePost) {

        String currentLikesToString = like_counter_tv.getText().toString();
        int currentLikesToInt = Integer.parseInt(currentLikesToString);

        if (likePost) {

            int newCurrentLikes = currentLikesToInt + 1;
            String newCurrentLikesToString = Integer.toString(newCurrentLikes);

            // Update likes on Real-time DB
            postsRef.child(postID).child("likes").setValue(newCurrentLikesToString);

            // update likes on TextView
            like_counter_tv.setText(newCurrentLikesToString);

            // Animate like counter TextView
            YoYo.with(Techniques.FadeInUp)
                    .duration(500)
                    .repeat(0)
                    .playOn(like_counter_tv);

        } else {

            int newCurrentLikes = currentLikesToInt - 1;
            String newCurrentLikesToString = Integer.toString(newCurrentLikes);

            // Update likes on Real-time DB
            postsRef.child(postID).child("likes").setValue(newCurrentLikesToString);

            // update likes on TextView
            like_counter_tv.setText(newCurrentLikesToString);

            // Animate like counter TextView
            YoYo.with(Techniques.FadeInDown)
                    .duration(500)
                    .repeat(0)
                    .playOn(like_counter_tv);
        }

    }

    private void deletePost() {

        StorageReference storageReference = FirebaseStorage.getInstance().getReferenceFromUrl(postImageURL);
        storageReference.delete()
                .addOnSuccessListener(unused -> Toast.makeText(context, "Post deleted", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());

        postsRef.child(postID).removeValue().addOnCompleteListener(task -> {
            like_btn.setVisibility(View.GONE);
            like_counter_tv.setVisibility(View.GONE);
            openCommentsView.setEnabled(false);
            show_comments_btn.setVisibility(View.GONE);
            commentsCount.setVisibility(View.GONE);
            username.setText("DELETED POST");
            postImg.setVisibility(View.GONE);
            profileImage.setImageResource(R.drawable.user);
            openUserProfileView.setEnabled(false);
            showPostOptionsButton.setVisibility(View.GONE);
        });

    }

    void sendNotificationToPostAuthor(String notificationType, String commentText) {

        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();

        final String[] notification_message = {"none"};
        final String[] notification_title = {"none"};

        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                String notificationID = usersRef.push().getKey();
                String currentDate = String.valueOf(android.text.format.DateFormat.format("dd-MM-yyyy", new java.util.Date()));

                Calendar calendar = Calendar.getInstance();
                int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
                int currentMinutes = calendar.get(Calendar.MINUTE);

                for (DataSnapshot snap : snapshot.getChildren()) {

                    if (snap.child("name").getValue(String.class) != null) {
                        if (Objects.equals(snap.child("name").getValue(String.class), username.getText().toString())) {

                            String postAuthorID = snap.child("id").getValue().toString();
                            usersRef.child(postAuthorID).child("notifications").child(notificationID).child("date").setValue(currentDate);

                            if (notificationType.equals("follow")) {
                                notification_title[0] = "New follower";
                                notification_message[0] = user.getDisplayName() + " started following you";
                                usersRef.child(postAuthorID).child("notifications").child(notificationID).child("title").setValue(notification_title[0]);
                                usersRef.child(postAuthorID).child("notifications").child(notificationID).child("type").setValue("new_follower");
                                usersRef.child(postAuthorID).child("notifications").child(notificationID).child("date").setValue(currentDate + "  " + currentHour + ":" + currentMinutes);
                                usersRef.child(postAuthorID).child("notifications").child(notificationID).child("message").setValue(notification_message[0]);
                                usersRef.child(postAuthorID).child("notifications").child(notificationID).child("post_id").setValue(postID);
                            }
                            if (notificationType.equals("like")) {
                                notification_title[0] = "New like";
                                notification_message[0] = user.getDisplayName() + " liked your post";
                                usersRef.child(postAuthorID).child("notifications").child(notificationID).child("title").setValue(notification_title[0]);
                                usersRef.child(postAuthorID).child("notifications").child(notificationID).child("type").setValue("like");
                                usersRef.child(postAuthorID).child("notifications").child(notificationID).child("date").setValue(currentDate + "  " + currentHour + ":" + currentMinutes);
                                usersRef.child(postAuthorID).child("notifications").child(notificationID).child("message").setValue(notification_message[0]);
                                usersRef.child(postAuthorID).child("notifications").child(notificationID).child("post_id").setValue(postID);
                            }
                            if (notificationType.equals("meme_saved")) {
                                notification_title[0] = "Meme saved";
                                notification_message[0] = user.getDisplayName() + " has saved your post";
                                usersRef.child(postAuthorID).child("notifications").child(notificationID).child("title").setValue("Meme saved");
                                usersRef.child(postAuthorID).child("notifications").child(notificationID).child("type").setValue("post_save");
                                usersRef.child(postAuthorID).child("notifications").child(notificationID).child("date").setValue(currentDate + "  " + currentHour + ":" + currentMinutes);
                                usersRef.child(postAuthorID).child("notifications").child(notificationID).child("message").setValue(user.getDisplayName() + " has saved your post");
                                usersRef.child(postAuthorID).child("notifications").child(notificationID).child("post_id").setValue(postID);
                            } else if (notificationType.equals("comment_added")) {
                                notification_title[0] = "New comment";
                                notification_message[0] = user.getDisplayName() + ": " + commentText;
                                usersRef.child(postAuthorID).child("notifications").child(notificationID).child("title").setValue(notification_title[0]);
                                usersRef.child(postAuthorID).child("notifications").child(notificationID).child("type").setValue("comment_added");
                                usersRef.child(postAuthorID).child("notifications").child(notificationID).child("date").setValue(currentDate + "  " + currentHour + ":" + currentMinutes);
                                usersRef.child(postAuthorID).child("notifications").child(notificationID).child("message").setValue(notification_message[0]);
                                usersRef.child(postAuthorID).child("notifications").child(notificationID).child("post_id").setValue(postID);
                            }

                            break;
                        }
                    }
                }

                // Find user token from DB
                // and add notification to Firestore
                for (DataSnapshot userSnap : snapshot.getChildren()) {
                    if (userSnap.child("name").getValue(String.class) != null) {
                        if (Objects.equals(userSnap.child("name").getValue(String.class), username.getText().toString())) {
                            if (userSnap.child("fcm_token").exists()) {
                                // add notification to Firestore to send
                                // push notification from back-end
                                Map<String, Object> notification = new HashMap<>();
                                notification.put("token", userSnap.child("fcm_token").getValue(String.class));
                                notification.put("title", notification_title[0]);
                                notification.put("message", notification_message[0]);
                                notification.put("not_type", notificationType);

                                if (notificationType.equals("follow")) {
                                    notification.put("userID", user.getUid());
                                } else {
                                    notification.put("postID", postID);
                                }

                                FirebaseFirestore firestore = FirebaseFirestore.getInstance();
                                firestore.collection("notifications")
                                        .document(notificationID).set(notification);
                            }
                            break;
                        }
                    }
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(context, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

    }

    private void shareToInstagramStory() {

        // Download image of post and bg image in order to get the image uri
        // Deleting these images after activity launched
        postImg.buildDrawingCache();
        Bitmap bmp = postImg.getDrawingCache();
        File storageLoc = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File file = new File(storageLoc, postID + ".jpg");

        try {

            FileOutputStream fos = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.close();
            scanFile(context, Uri.fromFile(file));

            // Saving story background to local storage
            Bitmap bm = BitmapFactory.decodeResource(context.getResources(), R.drawable.sm_instagram_story);
            File bg_file = new File(storageLoc, "bg_image.jpg");
            FileOutputStream fos_bg = new FileOutputStream(bg_file);
            bm.compress(Bitmap.CompressFormat.PNG, 100, fos_bg);
            fos.close();
            scanFile(context, Uri.fromFile(bg_file));

            Uri bg_uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", bg_file);
            Uri postImageUri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", file);

            Intent intent = new Intent("com.instagram.share.ADD_TO_STORY");
            intent.setType("image/*");
            intent.setPackage("com.instagram.android");
            String sourceApplication = "650794227085896";
            intent.putExtra("source_application", sourceApplication);
            intent.setDataAndType(bg_uri, "image/*");
            intent.putExtra("interactive_asset_uri", postImageUri);
            intent.putExtra("content_url", "https://play.google.com/store/apps/details?id=com.george.socialmeme&hl=en&gl=US");
            intent.putExtra("top_background_color", "#33FF33");
            intent.putExtra("bottom_background_color", "#33FF33");
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            activity.grantUriPermission(
                    "com.instagram.android", postImageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

            try {
                activity.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(activity, "Instagram is not installed!", Toast.LENGTH_SHORT).show();
            }

            // Delete temp images with delay
            // so the instagram activity can access them before deletion
            new Handler().postDelayed(() -> {
                bg_file.delete();
                file.delete();
            }, 6500);


        }  catch (FileNotFoundException e) {
            Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(context, "Error sharing story", Toast.LENGTH_SHORT).show();
        }

    }

    private void savePictureToDeviceStorage() {

        postImg.buildDrawingCache();
        Bitmap bmp = postImg.getDrawingCache();
        File storageLoc = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File file = new File(storageLoc, postID + ".jpg");

        try {
            FileOutputStream fos = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.close();
            scanFile(context, Uri.fromFile(file));
            Toast.makeText(context, "Meme saved in: " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            sendNotificationToPostAuthor("meme_saved", "");
        } catch (FileNotFoundException e) {
            Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(context, "Error saving file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

    }

    private static void scanFile(Context context, Uri imageUri) {
        Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        scanIntent.setData(imageUri);
        context.sendBroadcast(scanIntent);
    }

    void showShareOptions() {

        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.share_options_bottom_sheet);

        View shareToStory = dialog.findViewById(R.id.view13);
        View copyImageLink = dialog.findViewById(R.id.view15);

        shareToStory.setOnClickListener(v -> {
            PackageManager pm = context.getPackageManager();
            if (HomeActivity.isInstagramInstalled(pm)) {
                shareToInstagramStory();
            } else {
                Toast.makeText(context, "Instagram is not installed!", Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });

        copyImageLink.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("post_url", postImageURL);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, "Image URL copied to clipboard", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().getAttributes().windowAnimations = R.style.BottomSheetDialogAnimation;
        dialog.getWindow().setGravity(Gravity.BOTTOM);

    }

    void showPostOptionsBottomSheet() {

        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.post_options_bottom_sheet);

        View downloadMemeView = dialog.findViewById(R.id.view13);
        View reportPostView = dialog.findViewById(R.id.view15);
        View deletePostView = dialog.findViewById(R.id.view17);

        // Hide delete view if the current
        // logged in user is not
        // the author of the current post
        if (!username.getText().toString().equals(user.getDisplayName())) {
            dialog.findViewById(R.id.delete_post_view).setVisibility(View.GONE);
        }

        downloadMemeView.setOnClickListener(view -> {
            savePictureToDeviceStorage();
            dialog.dismiss();
        });

        reportPostView.setOnClickListener(view -> {
            DatabaseReference reportsRef = FirebaseDatabase.getInstance().getReference("reportedPosts");
            FirebaseAuth auth = FirebaseAuth.getInstance();
            reportsRef.child(auth.getCurrentUser().getUid()).setValue(postID).addOnCompleteListener(task -> {
                like_btn.setVisibility(View.GONE);
                like_btn.setVisibility(View.GONE);
                like_counter_tv.setVisibility(View.GONE);
                openCommentsView.setEnabled(false);
                show_comments_btn.setVisibility(View.GONE);
                commentsCount.setVisibility(View.GONE);
                username.setText("REPORTED POST");
                shareBtn.setVisibility(View.GONE);
                postImg.setVisibility(View.GONE);
                profileImage.setImageResource(R.drawable.user);
                openUserProfileView.setEnabled(false);
                showPostOptionsButton.setVisibility(View.GONE);
                reportsRef.child(postID);
                FirebaseDatabase.getInstance().getReference("posts").child(postID).child("reported").setValue("true");
                Toast.makeText(context, "Report received, thank you!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        });

        deletePostView.setOnClickListener(view -> {
            new AlertDialog.Builder(context)
                    .setTitle("Are you sure?")
                    .setMessage("Are you sure you want to delete this meme?. This action cannot be undone.")
                    .setIcon(R.drawable.ic_report)
                    .setPositiveButton("Yes", (dialogInterface, i) -> {
                        deletePost();
                        dialog.dismiss();
                    })
                    .setNegativeButton("No", (dialogInterface, i) -> dialogInterface.dismiss())
                    .show();
        });

        dialog.show();
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().getAttributes().windowAnimations = R.style.BottomSheetDialogAnimation;
        dialog.getWindow().setGravity(Gravity.BOTTOM);

    }

    void followPostAuthor() {

        usersRef.child(user.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.hasChild("following")) {
                    // Logged-in user follows post author
                    if (!snapshot.child("following").child(postAuthorID).exists()) {
                        usersRef.child(postAuthorID).child("followers").child(user.getUid()).setValue(user.getUid());
                        usersRef.child(user.getUid()).child("following").child(postAuthorID).setValue(postAuthorID);
                        followBtn.setTextColor(context.getColor(R.color.gray));
                        followBtn.setText("Following");
                        followBtn.setEnabled(false);
                        sendNotificationToPostAuthor("follow", "");
                        Toast.makeText(context, "You started following " + username.getText().toString(), Toast.LENGTH_SHORT).show();
                    }else {
                        Toast.makeText(context, "You already following this user.", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(context, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        
    }

    public ImageItemViewHolder(@NonNull View itemView) {
        super(itemView);

        final FirebaseAuth mAuth = FirebaseAuth.getInstance();
        final FirebaseUser user = mAuth.getCurrentUser();
        profileImage = itemView.findViewById(R.id.user_profile_image);
        container = itemView.findViewById(R.id.post_item_container);
        username = itemView.findViewById(R.id.post_username);
        postImg = itemView.findViewById(R.id.post_image);
        like_btn = itemView.findViewById(R.id.likeBtn);
        show_comments_btn = itemView.findViewById(R.id.show_comments_btn);
        showPostOptionsButton = itemView.findViewById(R.id.imageButton10);
        like_counter_tv = itemView.findViewById(R.id.like_counter);
        openUserProfileView = itemView.findViewById(R.id.view_profile);
        commentsCount = itemView.findViewById(R.id.textView63);
        shareBtn = itemView.findViewById(R.id.imageButton13);
        loadingProgressBar = itemView.findViewById(R.id.progressBar3);
        followBtn = itemView.findViewById(R.id.textView81);
        followBtnView = itemView.findViewById(R.id.follow_btn_view);
        openCommentsView = itemView.findViewById(R.id.openCommentsViewImageItem);

        if (activity != null) {
            Zoomy.Builder builder = new Zoomy.Builder(activity).target(postImg);
            builder.register();
        }

        showPostOptionsButton.setOnClickListener(view -> showPostOptionsBottomSheet());
        openCommentsView.setOnClickListener(view -> showCommentsDialog());
        shareBtn.setOnClickListener(view -> showShareOptions());

        if (HomeActivity.singedInAnonymously) {
            followBtnView.setVisibility(View.GONE);
            openUserProfileView.setEnabled(false);
        }

        followBtn.setOnClickListener(view -> followPostAuthor());

        openUserProfileView.setOnClickListener(v -> {

            if (postAuthorID != null) {
                if (postAuthorID.equals(user.getUid())) {
                    int selectedItemId = HomeActivity.bottomNavBar.getSelectedItemId();
                    if (selectedItemId != R.id.my_profile_fragment) {
                        HomeActivity.bottomNavBar.setItemSelected(R.id.my_profile_fragment, true);
                    }
                } else {
                    Intent intent = new Intent(context, UserProfileActivity.class);
                    intent.putExtra("user_id", postAuthorID);
                    intent.putExtra("username", username.getText().toString());
                    intent.putExtra("allPosts", new Gson().toJson(HomeActivity.savedPostsArrayList));
                    context.startActivity(intent);
                    CustomIntent.customType(context, "left-to-right");
                }
            }

        });

        openUserProfileView.setOnLongClickListener(view -> {
            copyUsernameToClipboard();
            return false;
        });

        // Check if logged-in user follows post author
        // to hide follow btn
        if (user != null) {
            usersRef.child(user.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.hasChild("following")) {
                        // Logged-in user follows post author
                        // hide follow btn
                        if (postAuthorID != null) {
                            if (snapshot.child("following").child(postAuthorID).exists()) {
                                followBtnView.setVisibility(View.GONE);
                            }
                        } else {
                            followBtnView.setVisibility(View.GONE);
                        }

                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(context, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }

        like_btn.setOnClickListener(v -> {

            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
            // Animate like button when clicked
            YoYo.with(Techniques.Shake)
                    .duration(500)
                    .repeat(0)
                    .playOn(like_btn);

            if (isPostLiked) {
                isPostLiked = false;
                like_btn.setImageResource(R.drawable.ic_like);
                likesRef.child(postID).child(user.getUid()).removeValue();
                updateLikesToDB(postID, false);
            } else {
                isPostLiked = true;
                like_btn.setImageResource(R.drawable.ic_like_filled);
                likesRef.child(postID).child(user.getUid()).setValue("true");
                updateLikesToDB(postID, true);

                if (!user.getUid().equals(postAuthorID)) {
                    sendNotificationToPostAuthor("like", "");
                }

            }

        });

    }

    boolean isNightModeEnabled() {
        SharedPreferences sharedPref = context.getSharedPreferences("dark_mode", MODE_PRIVATE);
        return sharedPref.getBoolean("dark_mode", false);
    }

    private void showCommentsDialog() {

        AlertDialog dialog;

        // Set dialog theme
        if (isNightModeEnabled()) {
            dialog = new AlertDialog.Builder(context, R.style.AppTheme_Base_Night).create();
            Window window = dialog.getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.BLACK);
        } else {
            dialog = new AlertDialog.Builder(context, R.style.Theme_SocialMeme).create();
            Window window = dialog.getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.WHITE);
        }

        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View dialogView = layoutInflater.inflate(R.layout.comments_dialog_fragment, null);

        CircleImageView profilePicture = dialogView.findViewById(R.id.comments_profile_image);
        ImageButton dismissDialogButton = dialogView.findViewById(R.id.imageButton17);
        EditText commentET = dialogView.findViewById(R.id.writeCommentET);
        ImageButton addCommentBtn = dialogView.findViewById(R.id.imageButton18);
        ProgressBar recyclerViewProgressBar = dialogView.findViewById(R.id.commentsProgressBar);
        RecyclerView commentsRecyclerView = dialogView.findViewById(R.id.comments_recycler_view);
        TextView noCommentsMsg = dialogView.findViewById(R.id.textView22);

        ArrayList<CommentModel> commentModelArrayList = new ArrayList<>();
        CommentsRecyclerAdapter adapter = new CommentsRecyclerAdapter(commentModelArrayList, context, dialog.getOwnerActivity());
        commentsRecyclerView.setAdapter(adapter);

        AdView mAdView = dialogView.findViewById(R.id.adView6);
        AdRequest adRequest = new AdRequest.Builder().build();
        if (HomeActivity.show_banners) mAdView.loadAd(adRequest);

        final LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setReverseLayout(true);
        layoutManager.setStackFromEnd(true);
        commentsRecyclerView.setAdapter(adapter);
        commentsRecyclerView.setHasFixedSize(true);
        commentsRecyclerView.setLayoutManager(layoutManager);

        // Load current user profile picture
        if (user.getPhotoUrl() != null) {
            Glide.with(context).load(user.getPhotoUrl().toString()).into(profilePicture);
        } else {
            profilePicture.setImageResource(R.drawable.user);
        }

        DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference();

        addCommentBtn.setOnClickListener(view -> {
            if (!commentET.getText().toString().isEmpty()) {

                ProgressBar progressBar = dialogView.findViewById(R.id.addCommentProgressBar);
                progressBar.setVisibility(View.VISIBLE);
                addCommentBtn.setVisibility(View.INVISIBLE);

                String commendID = rootRef.push().getKey();

                CommentModel commentModel = new CommentModel();
                commentModel.setAuthor(user.getUid());
                commentModel.setCommentID(commendID);
                commentModel.setAuthorUsername(user.getDisplayName());
                commentModel.setPostID(postID);
                commentModel.setCommentText(commentET.getText().toString());

                if (user.getPhotoUrl() != null) {
                    commentModel.setAuthorProfilePictureURL(user.getPhotoUrl().toString());
                } else {
                    commentModel.setAuthorProfilePictureURL("none");
                }

                // Update comment counter on post item inside RecyclerView
                String currentCommentsCountToString = commentsCount.getText().toString();
                int newCurrentCommentsCountToInt = Integer.parseInt(currentCommentsCountToString) + 1;
                commentsCount.setText(String.valueOf(newCurrentCommentsCountToInt));

                // Add comment to Firebase Real-Time database
                rootRef.child("posts").child(postID).child("comments").child(commendID).setValue(commentModel)
                        .addOnSuccessListener(unused -> {

                            // Add comment to RecyclerView
                            commentModelArrayList.add(commentModel);
                            adapter.notifyDataSetChanged();
                            adapter.notifyItemInserted(commentModelArrayList.size() - 1);

                            progressBar.setVisibility(View.GONE);
                            addCommentBtn.setVisibility(View.VISIBLE);
                            commentET.setText("");

                            // Hide no comments warning message if is visible
                            if (commentModelArrayList.size() == 1) {
                                noCommentsMsg.setVisibility(View.GONE);
                            }

                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(context, "Error: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                            progressBar.setVisibility(View.GONE);
                            addCommentBtn.setVisibility(View.VISIBLE);
                        });

                sendNotificationToPostAuthor("comment_added", commentET.getText().toString());

            } else {
                Toast.makeText(context, "Please write a comment", Toast.LENGTH_SHORT).show();
            }
        });

        rootRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.child("posts").child(postID).hasChild("comments")) {
                    for (DataSnapshot commentsSnapshot : snapshot.child("posts").child(postID).child("comments").getChildren()) {
                        CommentModel commentModel = new CommentModel();
                        commentModel.setAuthor(commentsSnapshot.child("author").getValue(String.class));
                        commentModel.setCommentID(commentsSnapshot.child("commentID").getValue(String.class));
                        commentModel.setAuthorUsername(commentsSnapshot.child("authorUsername").getValue(String.class));
                        commentModel.setPostID(commentsSnapshot.child("postID").getValue(String.class));
                        commentModel.setAuthorProfilePictureURL(commentsSnapshot.child("authorProfilePictureURL").getValue(String.class));
                        commentModel.setCommentText(commentsSnapshot.child("commentText").getValue(String.class));
                        commentModelArrayList.add(commentModel);
                        adapter.notifyDataSetChanged();
                        adapter.notifyItemInserted(commentModelArrayList.size() - 1);
                    }
                } else {
                    noCommentsMsg.setVisibility(View.VISIBLE);
                }
                recyclerViewProgressBar.setVisibility(View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(context, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        dismissDialogButton.setOnClickListener(view -> dialog.dismiss());

        dialog.setView(dialogView);
        dialog.getWindow().getAttributes().windowAnimations = R.style.BottomSheetDialogAnimation;
        dialog.show();

    }
}