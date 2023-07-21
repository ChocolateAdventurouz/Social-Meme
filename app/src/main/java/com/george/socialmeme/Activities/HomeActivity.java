package com.george.socialmeme.Activities;

import com.facebook.FacebookSdk;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.george.socialmeme.Fragments.HomeFragment;
import com.george.socialmeme.Fragments.MyProfileFragment;
import com.george.socialmeme.Fragments.NewPostFragment;
import com.george.socialmeme.Models.PostModel;
import com.george.socialmeme.Models.UserModel;
import com.george.socialmeme.R;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.ismaeldivita.chipnavigation.ChipNavigationBar;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import maes.tech.intentanim.CustomIntent;

public class HomeActivity extends AppCompatActivity {

    public static boolean singedInAnonymously = false;
    public static boolean showLoadingScreen;
    public static boolean appStarted;
    public static boolean watched_ad;
    public static boolean show_banners;
    public static ChipNavigationBar bottomNavBar;
    public static ArrayList<PostModel> savedPostsArrayList, noSuffledPostsList;
    public static ArrayList<UserModel> savedUserProfiles = null;
    public static UserModel savedUserData = null;
    public static ExtendedFloatingActionButton filtersBtn;

    public static boolean openNotification;
    public static String notiUserId;
    public static String notiPostId;
    public static String notiUsername;

    public static boolean userHasPosts = false;

    public static boolean isInstagramInstalled(PackageManager packageManager) {
        try {
            packageManager.getPackageInfo("com.instagram.android", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
    public static String prettyCount(Number number) {
        char[] suffix = {' ', 'k', 'M', 'B', 'T', 'P', 'E'};
        long numValue = number.longValue();
        int value = (int) Math.floor(Math.log10(numValue));
        int base = value / 3;
        if (value >= 3 && base < suffix.length) {
            return new DecimalFormat("#0.0").format(numValue / Math.pow(10, base * 3)) + suffix[base];
        } else {
            return new DecimalFormat("#,##0").format(numValue);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        //onDestroy();
        //finish();
    }

    void followAppCreator() {

        String creatorUserId = "cFNlK7QLLjZgc7SQDp79PwESxbB2";
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();

        if (!user.getUid().equals(creatorUserId)) {
            // Add to logged-in user to followers list
            DatabaseReference creatorRef = FirebaseDatabase.getInstance().getReference("users").child(creatorUserId);
            creatorRef.child("followers").child(user.getUid()).setValue(user.getUid());

            // Add creator to logged-in user following list
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(user.getUid());
            userRef.child("following").child(creatorUserId).setValue(creatorUserId);

            // Add notification to firestore
            creatorRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {

                    String token = snapshot.child("fcm_token").getValue(String.class);

                    String firestoreNotificationID = userRef.push().getKey();
                    Map<String, Object> notification = new HashMap<>();
                    notification.put("token", token);
                    notification.put("userID", user.getUid());
                    notification.put("not_type", "follow");
                    notification.put("title", "New follower");
                    notification.put("message", user.getDisplayName() + " started following you");
                    FirebaseFirestore firestore = FirebaseFirestore.getInstance();
                    firestore.collection("notifications")
                            .document(firestoreNotificationID).set(notification);

                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {

                }
            });

        }

    }

    boolean isNightModeEnabled() {
        SharedPreferences sharedPref = getSharedPreferences("dark_mode", MODE_PRIVATE);
        return sharedPref.getBoolean("dark_mode", false);
    }

    boolean newFeaturesViewed() {
        SharedPreferences sharedPref = getSharedPreferences("v2.2.7", MODE_PRIVATE);
        return sharedPref.getBoolean("v2.2.7", false);
    }

    void enableNightMode() {
        SharedPreferences sharedPref = getSharedPreferences("dark_mode", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean("dark_mode", true);
        editor.apply();
        Toast.makeText(this, "Night mode enabled", Toast.LENGTH_SHORT).show();
    }

    boolean followedCreator() {
        SharedPreferences sharedPref = getSharedPreferences("follow_creator", Context.MODE_PRIVATE);
        return sharedPref.getBoolean("follow_creator", false);
    }

    public interface FirebaseCallback {
        void onCallback(boolean show);
    }

    FirebaseCallback firebaseCallback = show -> {

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();

        if (getSupportFragmentManager() != null) {
            this.show_banners = show;

            bottomNavBar.setOnItemSelectedListener(id -> {

                Fragment selectedFragment = new HomeFragment();

                switch (id) {
                    case R.id.home_fragment:
                        selectedFragment = new HomeFragment();
                        filtersBtn.setVisibility(View.VISIBLE);
                        break;
                    case R.id.new_post_fragment:
                        selectedFragment = new NewPostFragment();
                        filtersBtn.setVisibility(View.INVISIBLE);
                        break;
                    case R.id.my_profile_fragment:
                        selectedFragment = new MyProfileFragment();
                        filtersBtn.setVisibility(View.INVISIBLE);
                        break;
                }

                getSupportFragmentManager()
                        .beginTransaction()
                        .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();

            });

            // Load default fragment
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new HomeFragment()).commit();
            bottomNavBar.setItemSelected(R.id.home_fragment, true);
        }

        if (!singedInAnonymously && user != null) {
            user.reload().addOnFailureListener(e -> new AlertDialog.Builder(HomeActivity.this)
                    .setTitle("Error")
                    .setMessage(e.getMessage())
                    .setCancelable(false)
                    .setPositiveButton("Ok", (dialog, which) -> {
                        finish();
                        startActivity(new Intent(HomeActivity.this, LoginActivity.class));
                    }).show());

            if (!followedCreator()) {
                followAppCreator();
                SharedPreferences sharedPref = getSharedPreferences("follow_creator", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putBoolean("follow_creator", true);
                editor.apply();
            }

        }

        bottomNavBar.setVisibility(View.VISIBLE);
        filtersBtn.setVisibility(View.VISIBLE);

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        if (isNightModeEnabled()) {
            // Using force apply because HomeActivity contains fragments
            Resources.Theme theme = super.getTheme();
            theme.applyStyle(R.style.AppTheme_Base_Night, true);
        }

        // Decide if we show AD banners based on DB variable
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference();
        ref.child("show_banners").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean showAdBanner = (Boolean) snapshot.getValue(Boolean.class);
                firebaseCallback.onCallback(showAdBanner);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(HomeActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        /*
        if (!newFeaturesViewed()) {
            SharedPreferences sharedPref = getSharedPreferences("v2.2.7", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putBoolean("v2.2.7", true);
            editor.apply();
            startActivity(new Intent(HomeActivity.this, NewsActivity.class));
            CustomIntent.customType(HomeActivity.this, "fadein-to-fadeout");
        }*/

        Bundle extras = getIntent().getExtras();

        if (extras != null) {

            openNotification = true;

            if (extras.getString("user_id") != null) {
                notiUserId = extras.getString("user_id");
            }

            if (extras.getString("post_id") != null) {
                notiPostId = extras.getString("post_id");
            }

            if (extras.getString("username") != null) {
                notiUsername = extras.getString("username");
            }

        }

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.BLUE);

        bottomNavBar = findViewById(R.id.bottom_nav);
        filtersBtn = findViewById(R.id.filters_btn);
        savedPostsArrayList = new ArrayList<>();
        savedUserProfiles = new ArrayList<>();
        noSuffledPostsList = new ArrayList<>();

        // Hide navBar and filterBtn while loading
        bottomNavBar.setVisibility(View.INVISIBLE);
        filtersBtn.setVisibility(View.INVISIBLE);

        // Detect if system night mode is enabled
        // to auto enable in-app night mode
        SharedPreferences askForNightModeSharedPref = getSharedPreferences("asked_night_mode_enable", MODE_PRIVATE);
        SharedPreferences.Editor askForNightModeSharedPrefEditor = askForNightModeSharedPref.edit();
        boolean askForEnableNightMode = askForNightModeSharedPref.getBoolean("asked_night_mode_enable", false);
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;

        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES && !isNightModeEnabled() && !askForEnableNightMode) {

            // System dark mode is enabled
            // ask user to enable in-app night mode
            AlertDialog alertDialog = new AlertDialog.Builder(HomeActivity.this)
                    .setCancelable(false)
                    .setTitle("Enable app night mode?")
                    .setIcon(R.drawable.moon)
                    .setMessage("Social Meme detected that you have enabled night mode on your device. " +
                            "You want to enable night mode in Social Meme too?")
                    .setPositiveButton("Yes", (dialogInterface, i) -> {
                        enableNightMode();
                        finish();
                        startActivity(new Intent(HomeActivity.this, SplashScreenActivity.class));
                    })
                    .setNegativeButton("No, thanks", (dialogInterface, i) -> {
                        askForNightModeSharedPrefEditor.putBoolean("asked_night_mode_enable", true);
                        askForNightModeSharedPrefEditor.apply();
                        dialogInterface.dismiss();
                        /*
                        AlertDialog reminderDialog = new AlertDialog.Builder(HomeActivity.this)
                                .setTitle("Nigh mode")
                                .setIcon(R.drawable.moon)
                                .setMessage("Remember that you can always enable night mode in Social Meme settings")
                                .setNegativeButton("Ok", (dialogInterface1, i1) -> dialogInterface1.dismiss())
                                .create()


                        reminderDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                        reminderDialog.getWindow().setBackgroundDrawableResource(R.drawable.custom_dialog_background);
                        reminderDialog.show();
                        */
                    }).create();

            alertDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            alertDialog.getWindow().setBackgroundDrawableResource(R.drawable.custom_dialog_background);
            alertDialog.show();

        }

    }
}