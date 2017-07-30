/*****************************************************************************
 * AudioPlayer.java
 *****************************************************************************
 * Copyright © 2011-2014 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc.gui.audio;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import org.videolan.medialibrary.Medialibrary;
import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.databinding.generated.callback.OnClickListener;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresPermission;
import android.support.constraint.ConstraintSet;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.Snackbar;
import android.support.transition.TransitionManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.widget.TextView;
import android.widget.Toast;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.ClipActivity;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.databinding.AudioPlayerBinding;
import org.videolan.vlc.gui.AudioPlayerContainerActivity;
import org.videolan.vlc.gui.PlaybackServiceFragment;
import org.videolan.vlc.gui.dialogs.AdvOptionsDialog;
import org.videolan.vlc.gui.dialogs.ClipFragment;
import org.videolan.vlc.gui.dialogs.DataObject;
import org.videolan.vlc.gui.dialogs.JumpToTimeDialog;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.SwipeDragItemTouchHelperCallback;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.gui.view.AudioMediaSwitcher.AudioMediaSwitcherListener;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Strings;

public class AudioPlayer extends PlaybackServiceFragment implements PlaybackService.Callback, PlaylistAdapter.IPlayer, TextWatcher,
        ClipFragment.OnOkButtonClickListener{


    public static ArrayList<DataObject> dataString = new ArrayList<DataObject>();

    public long begTime = 0;
    public long endTime = 30000*1000;
    public String strEtime="";


    public static long sbegTime = 0;
    public static long sendTime = 30000*1000;
    public static String sstrEtime="";


    public static String title_1 = "";
    public static String loc_1 = "";

    protected static long MILLIS_IN_MICROS = 1000;
    protected static long SECONDS_IN_MICROS = 1000 * MILLIS_IN_MICROS;
    protected static long MINUTES_IN_MICROS = 60 * SECONDS_IN_MICROS;
    protected static long HOURS_IN_MICROS = 60 * MINUTES_IN_MICROS;

    public static final String TAG = "VLC/AudioPlayer";

    private static int DEFAULT_BACKGROUND_DARKER_ID;
    private static int DEFAULT_BACKGROUND_ID;
    public static final int SEARCH_TIMEOUT_MILLIS = 5000;

    private AudioPlayerBinding mBinding;

    private boolean mShowRemainingTime = false;
    private boolean mPreviewingSeek = false;

    private PlaylistAdapter mPlaylistAdapter;

    private boolean mAdvFuncVisible;
    private boolean mPlaylistSwitchVisible;
    private boolean mSearchVisible;
    private boolean mHeaderPlayPauseVisible;
    private boolean mProgressBarVisible;
    private boolean mHeaderTimeVisible;
    private int mPlayerState;
    private String mCurrentCoverArt;
    private final ConstraintSet coverConstraintSet = AndroidUtil.isICSOrLater ? new ConstraintSet() : null;
    private final ConstraintSet playlistConstraintSet = AndroidUtil.isICSOrLater ? new ConstraintSet() : null;

    // Tips
    private static final String PREF_PLAYLIST_TIPS_SHOWN = "playlist_tips_shown";
    private static final String PREF_AUDIOPLAYER_TIPS_SHOWN = "audioplayer_tips_shown";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mBinding = AudioPlayerBinding.inflate(inflater);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (AndroidUtil.isJellyBeanMR1OrLater) {
            DEFAULT_BACKGROUND_DARKER_ID = UiTools.getResourceFromAttribute(view.getContext(), R.attr.background_default_darker);
            DEFAULT_BACKGROUND_ID = UiTools.getResourceFromAttribute(view.getContext(), R.attr.background_default);
        }
        mPlaylistAdapter = new PlaylistAdapter(this);
        mBinding.songsList.setLayoutManager(new LinearLayoutManager(mBinding.getRoot().getContext()));
        mBinding.songsList.setAdapter(mPlaylistAdapter);
        mBinding.audioMediaSwitcher.setAudioMediaSwitcherListener(mHeaderMediaSwitcherListener);
        mBinding.coverMediaSwitcher.setAudioMediaSwitcherListener(mCoverMediaSwitcherListener);
        mBinding.playlistSearchText.getEditText().addTextChangedListener(this);

        ItemTouchHelper.Callback callback =  new SwipeDragItemTouchHelperCallback(mPlaylistAdapter);
        ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(mBinding.songsList);

        setHeaderVisibilities(false, false, true, false, true, false);
        mBinding.setFragment(this);

        mBinding.next.setOnTouchListener(new LongSeekListener(true,
                UiTools.getResourceFromAttribute(view.getContext(), R.attr.ic_next),
                R.drawable.ic_next_pressed));
        mBinding.previous.setOnTouchListener(new LongSeekListener(false,
                UiTools.getResourceFromAttribute(view.getContext(), R.attr.ic_previous),
                R.drawable.ic_previous_pressed));

        registerForContextMenu(mBinding.songsList);
        getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setUserVisibleHint(true);
        if (playlistConstraintSet != null) {
            playlistConstraintSet.clone(mBinding.contentLayout);
            coverConstraintSet.clone(mBinding.contentLayout);
            coverConstraintSet.setVisibility(R.id.songs_list, View.GONE);
            coverConstraintSet.setVisibility(R.id.cover_media_switcher, View.VISIBLE);
        }


        ((Button) view.findViewById(R.id.buttonInv3)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                // When button is clicked, call up to owning activity.
                //caller.doCancelConfirmClick();

                onBtnInv3(view);
            }
        });


        ImageView fashionImg = (ImageView) view.findViewById(R.id.Trim2);
        // set a onclick listener for when the button gets clicked
        fashionImg.setOnClickListener(new View.OnClickListener() {
            // Start new list activity
            public void onClick(View v) {
                Log.d("audior","rocking!!");
                onTrim(view);

            }
        });



    }

    public void onPopupMenu(View anchor, final int position) {
        final Activity activity = getActivity();
        if (activity == null || position >= mPlaylistAdapter.getItemCount())
            return;
        final MediaWrapper mw = mPlaylistAdapter.getItem(position);
        final PopupMenu popupMenu = new PopupMenu(activity, anchor);
        popupMenu.getMenuInflater().inflate(R.menu.audio_player, popupMenu.getMenu());

        popupMenu.getMenu().setGroupVisible(R.id.phone_only, mw.getType() != MediaWrapper.TYPE_VIDEO
                && TextUtils.equals(mw.getUri().getScheme(), "file")
                && AndroidDevices.isPhone());

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.audio_player_mini_remove) {
                    if (mService != null) {
                        mService.remove(position);
                        return true;
                    }
                } else if (item.getItemId() == R.id.audio_player_set_song) {
                    AudioUtil.setRingtone(mw, activity);
                    return true;
                }
                return false;
            }
        });
        popupMenu.show();
    }

    /**
     * Show the audio player from an intent
     *
     * @param context The context of the activity
     */
    public static void start(Context context) {
        Intent intent = new Intent();
        intent.setAction(AudioPlayerContainerActivity.ACTION_SHOW_PLAYER);
        context.getApplicationContext().sendBroadcast(intent);
    }

    public void update() {
        mHandler.removeMessages(UPDATE);
        mHandler.sendEmptyMessage(UPDATE);
    }

    public void doUpdate() {
        if (mService == null || getActivity() == null)
            return;
        if (mService.hasMedia() && !mService.isVideoPlaying()) {
            SharedPreferences mSettings= PreferenceManager.getDefaultSharedPreferences(getActivity());
            //Check fragment resumed to not restore video on device turning off
            if (isVisible() && mSettings.getBoolean(PreferencesActivity.VIDEO_RESTORE, false)) {
                mSettings.edit().putBoolean(PreferencesActivity.VIDEO_RESTORE, false).apply();
                mService.getCurrentMediaWrapper().removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
                mService.switchToVideo();
                return;
            } else
                show();
        } else {
            hide();
            return;
        }

        mBinding.audioMediaSwitcher.updateMedia(mService);
        mBinding.coverMediaSwitcher.updateMedia(mService);

        FragmentActivity act = getActivity();
        mBinding.playlistPlayasaudioOff.setVisibility(mService.getVideoTracksCount() > 0 ? View.VISIBLE : View.GONE);

        boolean playing = mService.isPlaying();
        int imageResId = UiTools.getResourceFromAttribute(act, playing ? R.attr.ic_pause : R.attr.ic_play);
        String text = getString(playing ? R.string.pause : R.string.play);
        mBinding.playPause.setImageResource(imageResId);
        mBinding.playPause.setContentDescription(text);
        mBinding.headerPlayPause.setImageResource(imageResId);
        mBinding.headerPlayPause.setContentDescription(text);
        mBinding.shuffle.setImageResource(UiTools.getResourceFromAttribute(act, mService.isShuffling() ? R.attr.ic_shuffle_on : R.attr.ic_shuffle));
        mBinding.shuffle.setContentDescription(getResources().getString(mService.isShuffling() ? R.string.shuffle_on : R.string.shuffle));
        switch(mService.getRepeatType()) {
            case PlaybackService.REPEAT_NONE:
                mBinding.repeat.setImageResource(UiTools.getResourceFromAttribute(act, R.attr.ic_repeat));
                mBinding.repeat.setContentDescription(getResources().getString(R.string.repeat));
                break;
            case PlaybackService.REPEAT_ONE:
                mBinding.repeat.setImageResource(UiTools.getResourceFromAttribute(act, R.attr.ic_repeat_one));
                mBinding.repeat.setContentDescription(getResources().getString(R.string.repeat_single));
                break;
            default:
            case PlaybackService.REPEAT_ALL:
                mBinding.repeat.setImageResource(UiTools.getResourceFromAttribute(act, R.attr.ic_repeat_all));
                mBinding.repeat.setContentDescription(getResources().getString(R.string.repeat_all));
                break;
        }
        mBinding.shuffle.setVisibility(mService.canShuffle() ? View.VISIBLE : View.INVISIBLE);
        mBinding.timeline.setOnSeekBarChangeListener(mTimelineListner);
        updateList();
        updateBackground();
    }

    @Override
    public void updateProgress() {
        if (mService == null)
            return;

        int time = (int) mService.getTime();

        if (time > endTime) {
            mService.pause();
            String st2 = "";
            System.out.println(st2);

            /*
            MediaDatabase db = MediaDatabase.getInstance();
            st2 = db.getClips();

            String st3 = mService.getTitle();
            String st4 = mService.getMediaLocations().get(0);

            db.putClips(st3,st4,"33-43||");


            List<String> n1 =  mService.getMediaLocations();

            String st2 = n1.get(0);
            for(int i = 0; i < n1.size(); i++) {
                System.out.println(n1.get(i));

            }
            n1.add(1,"hello");
            */
            System.out.println(st2+"te2");


        }

        int length = (int) mService.getLength();
        //System.out.println("length="+length);

        mBinding.headerTime.setText(Tools.millisToString(time));
        mBinding.length.setText(Tools.millisToString(length));
        mBinding.timeline.setMax(length);
        mBinding.progressBar.setMax(length);

        if (!mPreviewingSeek) {
            mBinding.time.setText(Tools.millisToString(mShowRemainingTime ? time-length : time));
            mBinding.timeline.setProgress(time);
            mBinding.progressBar.setProgress(time);
        }
    }

    @Override
    public void onMediaEvent(Media.Event event) {}

    @Override
    public void onMediaPlayerEvent(MediaPlayer.Event event) {
        switch (event.type) {
            case MediaPlayer.Event.Opening:
                hideSearchField();
                break;
            case MediaPlayer.Event.Stopped:
                hide();
                break;
        }
    }

    private void updateBackground() {
        if (AndroidUtil.isJellyBeanMR1OrLater) {
            final MediaWrapper mw = mService.getCurrentMediaWrapper();
            if (mw == null || TextUtils.equals(mCurrentCoverArt, mw.getArtworkMrl()))
                return;
            mCurrentCoverArt = mw.getArtworkMrl();
            if (TextUtils.isEmpty(mw.getArtworkMrl())) {
                setDefaultBackground();
            } else {
                VLCApplication.runBackground(new Runnable() {
                    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
                    @Override
                    public void run() {
                        final Bitmap blurredCover = UiTools.blurBitmap(AudioUtil.readCoverBitmap(Uri.decode(mw.getArtworkMrl()), mBinding.contentLayout.getWidth()));
                        if (blurredCover != null)
                            VLCApplication.runOnMainThread(new Runnable() {
                                @Override
                                public void run() {
                                    AudioPlayerContainerActivity activity = (AudioPlayerContainerActivity) getActivity();
                                    if (activity == null)
                                        return;
                                    mBinding.backgroundView.setColorFilter(UiTools.getColorFromAttribute(activity, R.attr.audio_player_background_tint));
                                    mBinding.backgroundView.setImageBitmap(blurredCover);
                                    mBinding.backgroundView.setVisibility(View.VISIBLE);
                                    mBinding.songsList.setBackgroundResource(0);
                                }
                            });
                        else
                            setDefaultBackground();
                    }
                });
            }
        }
        if (((AudioPlayerContainerActivity)getActivity()).isAudioPlayerExpanded())
            setHeaderVisibilities(true, true, false, false, false, true);

    }

    private void setDefaultBackground() {
        mBinding.songsList.setBackgroundResource(DEFAULT_BACKGROUND_ID);
        mBinding.backgroundView.setVisibility(View.INVISIBLE);
    }

    public void updateList() {
        hideSearchField();
        if (mService != null)
            mPlaylistAdapter.update(mService.getMedias());
    }

    @Override
    public void onSelectionSet(int position) {
        if (mPlayerState != BottomSheetBehavior.STATE_COLLAPSED && mPlayerState != BottomSheetBehavior.STATE_HIDDEN)
            mBinding.songsList.smoothScrollToPosition(position);
    }

    OnSeekBarChangeListener mTimelineListner = new OnSeekBarChangeListener() {

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
            if (fromUser && mService != null) {
                mService.setTime(progress);
                mBinding.time.setText(Tools.millisToString(mShowRemainingTime ? progress-mService.getLength() : progress));
                mBinding.headerTime.setText(Tools.millisToString(progress));
            }
        }
    };

    public void onTimeLabelClick(View view) {
        mShowRemainingTime = !mShowRemainingTime;
        update();
    }

    public void onPlayPauseClick(View view) {
        if (mService == null)
            return;
        if (mService.isPlaying()) {
            mService.pause();
        } else {
            Log.d("audior",""+endTime);
            //endTime = 30000*1000;
            endTime = convTime(sstrEtime);
            mService.play();
        }
    }


    public void onComplete(ArrayList<DataObject> data) {

        DataObject d1 = data.get(0);

        System.out.println(d1.getmText1());
        dataString   = data;

    }

    public void passData(ArrayList<DataObject> data){


        dataString   = data;

        /*
        MediaDatabase db = MediaDatabase.getInstance();
        db.getClips(title_1,loc_1);

        stArr.add("00:01:20-00:01:35");
*/

        String st9 = "";
        for (int j = 0; j < data.size(); j++) {
            DataObject d1 = data.get(j);
            String a1 = d1.getmText1();
            String a2 = d1.getmText2();
            String a3 = d1.getmText3();
            if (convTime(a2) > convTime(sstrEtime)) {
               // a2 = convVerStr(Tools.millisToString(endTime));
                a2 = sstrEtime;
                //a2 = convVerStr(Tools.millisToString(sendTime));
            }
            if (convTime(a2) < convTime(a1)) {
                // a2 = convVerStr(Tools.millisToString(endTime));
                a2 = sstrEtime;
                //a2 = convVerStr(Tools.millisToString(sendTime));
            }
            if (convTime(a1) > convTime(a2)) {
                // a2 = convVerStr(Tools.millisToString(endTime));
                a1 = "00:00:00";
                //a2 = convVerStr(Tools.millisToString(sendTime));
            }


            if (a3.equals("#9"))
                a3 = "";
            String a4 = a1+"-"+a2+"-"+a3;
            st9 = st9+a4 + ";";
        }


        MediaDatabase db = MediaDatabase.getInstance();

        Log.d("DB_1","database before insert");
        db.getClips2();
        db.putClips3(loc_1,title_1,st9);
        db.putClips4();
        Log.d("DB_1","database after insert");
        db.getClips2();

    }

    public long convTime(String st3){



        String t1[] = st3.split(":");


        String mHours=t1[0],mMinutes=t1[1],mSeconds=t1[2];
        long hours, minutes, seconds;

        hours = !mHours.equals("") ? Long.parseLong(mHours) * HOURS_IN_MICROS : 0l;
        minutes = !mMinutes.equals("") ? Long.parseLong(mMinutes) * MINUTES_IN_MICROS : 0l;
        seconds = !mSeconds.equals("") ? Long.parseLong(mSeconds) * SECONDS_IN_MICROS : 0l;


        long time = (hours+minutes+seconds)/1000l;
        return time;

    }



    public void onBtnInv3(View v2){

        Button b2 = ((Button) v2.findViewById(R.id.buttonInv3));


        String t3 =  b2.getTag(R.string.name)+"";
        int p1 = Integer.parseInt(t3);

        DataObject d1 = dataString.get(p1);

        String t1[] = (d1.getmText1()).split(":");


        String mHours=t1[0],mMinutes=t1[1],mSeconds=t1[2];
        long hours, minutes, seconds;

        hours = !mHours.equals("") ? Long.parseLong(mHours) * HOURS_IN_MICROS : 0l;
        minutes = !mMinutes.equals("") ? Long.parseLong(mMinutes) * MINUTES_IN_MICROS : 0l;
        seconds = !mSeconds.equals("") ? Long.parseLong(mSeconds) * SECONDS_IN_MICROS : 0l;


        begTime = (hours+minutes+seconds)/1000l;
        System.out.println(" h="+ hours+" m="+ minutes+" s= "+ seconds + " beg "+begTime);


        String t2[] = (d1.getmText2()).split(":");
        mHours=t2[0];
        mMinutes=t2[1];
        mSeconds=t2[2];

        hours = !mHours.equals("") ? Long.parseLong(mHours) * HOURS_IN_MICROS : 0l;
        minutes = !mMinutes.equals("") ? Long.parseLong(mMinutes) * MINUTES_IN_MICROS : 0l;
        seconds = !mSeconds.equals("") ? Long.parseLong(mSeconds) * SECONDS_IN_MICROS : 0l;

        endTime = (hours+minutes+seconds)/1000l;
        sendTime = endTime;

        if (endTime > convTime(sstrEtime)) {
            endTime = convTime(sstrEtime);
        }
        if (endTime < begTime) {
            endTime = convTime(sstrEtime);

        }
        if (begTime > endTime) {
            begTime = convTime("00:00:00");
        }


/*
        if (mService.isPlaying()) {

            mService.pause();
            mService.setTime(begTime);
            mService.play();
            //updateProgress();
        } else {
            mService.setTime(begTime);
            mService.play();
            //updateProgress();
        }
*/
        mService.pause();
        mService.setTime(begTime);
        updateProgress();
        mService.play();


    }

    public String convVerStr(String st3){

        String t4[] = st3.split(":");
        String hh="", mm="",ss="";


        //0:023 where 0=t4[0] and 023 is t4[1]
        //90:9:02 where 90=t4[0] and 9 is t4[1] and 023 is t4[2]
        String validStr = "";

        int ln1 = st3.indexOf(":");
        ln1 = st3.length() - st3.replace(":", "").length();

        if (ln1<1 || ln1>2){
            Log.d("T9", "Error "+ln1);
            validStr = "Err";
        }
        else {
            //0:023 where 0=t4[0] and 023 is t4[1]

            if (ln1 == 1) {
                if (t4[0].length()>2 || t4[1].length()>2)
                    validStr = "Err";
                else {
                    hh="00";
                    mm=t4[0];
                    ss=t4[1];

                    if (mm.length() == 1) {
                        //i.e 1:23 then add "0" to 1 and make it 01:23
                        validStr = "0" + t4[0] + ":" ;
                        mm = "0"+mm;

                    }
                    else
                        validStr = validStr + mm + ":";
                    if (ss.length() == 1) {
                        //i.e 01:2 then add "0" to 2 and make it 01:20
                        validStr = validStr + ":" + "0" + ss ;
                        ss = "0"+ss;
                    }
                    else
                        validStr = validStr  +ss;


                    validStr = hh + ":"+ validStr;
                    Log.d("T9", "suc1 "+validStr);

                }

            }
            if (ln1 == 2) {
                //0:01:02
                if (t4[0].length()>2 || t4[1].length()>2 || t4[2].length()>2)
                    validStr = "Err";
                else {
                    hh=t4[0];
                    mm=t4[1];
                    ss=t4[2];

                    if (hh.length() == 1) {
                        validStr = "0" + t4[0] + ":" ;
                        hh = "0"+hh;

                    }
                    else
                        validStr = validStr + hh + ":";

                    if (mm.length() == 1) {
                        //i.e 1:23 then add "0" to 1 and make it 01:23
                        validStr = validStr+ ":"+ "0" + t4[1];
                        mm = "0"+mm;

                    }
                    else
                        validStr = validStr + mm + ":";

                    if (ss.length() == 1) {
                        //i.e 01:2 then add "0" to 2 and make it 01:20
                        validStr = validStr + "0" + ss ;
                        ss = "0"+ss;
                    }
                    else
                        validStr = validStr  +ss;

                    Log.d("T9", "suc2 "+validStr);

                }

            }

        }

        if (validStr.equals("Err") == false) {
            String t5[] = validStr.split(":");
            boolean strEr = false;

            if (Integer.parseInt(t5[0]) > 99 || Integer.parseInt(t5[1]) > 59 || Integer.parseInt(t5[2]) > 59) {
                strEr = true;
                validStr = "Err";
                Log.d("T9", "Error2 " + validStr);

            }
        }

        Log.d("T9", "Return - "+validStr);



        return validStr;
    }

    //test


    public void onTrim(View view){
        //startActivity(new Intent(VLCApplication.getAppContext(), ClipActivity.class));
        try {

            if (mService == null)
            return;


            title_1 = mService.getTitle();
            loc_1 = mService.getMediaLocations().get(0);
            MediaDatabase db = MediaDatabase.getInstance();
            Log.d("DB_2",title_1+"1t-2l"+loc_1);


             //db.deleteClipTable();



         //  db.putClips3(loc_1,title_1,"00:01:20-00:01:35;00:00:20-00:01:35;");

    /*
            db.putClips3(loc_1,title_1,"string-1");
            db.getClips2();
            db.putClips3(loc_1,title_1,"string-3");
            db.getClips2();
    */


            //db.putClips(title_1,loc_1,"test2");

            String valString = db.getClips3(loc_1);


            if (valString.equals("")) {
                dataString.clear();
            }
            else{


                String t3[] = valString.split(";");
                Log.d("T",t3.length+"");

                dataString.clear();

                for (int i = 0; i < t3.length; i++){
                    Log.d("T",t3[i]);
                    String t4[] = t3[i].split("-");

                    DataObject obj=null;
                    if (t4.length == 3) {
                        obj = new DataObject(t4[0],
                                t4[1] , t4[2]);
                        dataString.add(obj);

                    }
                    if (t4.length == 2) {
                        obj = new DataObject(t4[0],
                                t4[1] , "#9");
                        dataString.add(obj);
                    }

                }



            }




          //String key = "hh:mm:ss-hh:mm:ss1;hh:mm:ss-hh:mm:ss2";

            ArrayList<String> stArr = new ArrayList<String>();

            String st3 = mService.getTitle();
            String st4 = mService.getCurrentMediaLocation();

            String st7 = Tools.millisToString(mService.getLength());
            String st8 = convVerStr(st7);
            if (dataString.size() == 0) {
                stArr = new ArrayList<String>();
                stArr.add("00:00:00-"+st8+"-#9");
            }
            else{


                for (int i = 0; i < dataString.size();i++){
                    // MyRecyclerViewAdapter.DataObjectHolder.get(i)
                    DataObject d1 = dataString.get(i);
                    String st1 = d1.getmText1();
                    String en2 = d1.getmText2();
                    String nt2 = d1.getmText3();
                    stArr.add(st1+"-"+en2+"-"+nt2);
                }
            }
            //FragmentManager fm = getFragmentManager();
            //http://stackoverflow.com/questions/13443811/cannot-call-getsupportfragmentmanager-from-activity
            FragmentManager fm = getActivity().getSupportFragmentManager();

            Bundle args = new Bundle();
            //args.putString("key", stringVal);
            args.putString("type", "audio");
            args.putString("title",st3);
            args.putStringArrayList("key", stArr);
            args.putString("path",st4);

            int time = (int) mService.getTime();
            String st9 = convVerStr(Tools.millisToString(time));
            args.putString("cTime",st9);
            args.putString("eTime",st8);
            strEtime = st8;
            sstrEtime = strEtime;

            //args.putStringArrayList("key2",x);
            ClipFragment dFragment1 = new ClipFragment();
            dFragment1.show(fm, "Dialog Fragment");
            dFragment1.setArguments(args);

            //dFragment1.setListener(this);

        }
        catch(Exception e)
        {
            Log.i("err",e.toString());

            Toast.makeText(getActivity(), "Error opening VlcClipText",
                    Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }

/*
        FragmentManager fm = getFragmentManager();
        ClipFragment dFragment = new ClipFragment();
        // Show DialogFragment
        dFragment.show(fm, "Dialog Fragment");
*/



        //dismiss();


    }

    public boolean onStopClick(View view) {
        if (mService == null)
            return false;
        mService.stop();
        return true;
    }

    public void onNextClick(View view) {
        if (mService == null)
            return;
        if (mService.hasNext())
            mService.next();
        else
            Snackbar.make(getView(), R.string.lastsong, Snackbar.LENGTH_SHORT).show();
    }

    public void onPreviousClick(View view) {
        if (mService == null)
            return;
        if (mService.hasPrevious() || mService.isSeekable())
            mService.previous(false);
        else
            Snackbar.make(getView(), R.string.firstsong, Snackbar.LENGTH_SHORT).show();
    }

    public void onRepeatClick(View view) {
        if (mService == null)
            return;

        switch (mService.getRepeatType()) {
            case PlaybackService.REPEAT_NONE:
                mService.setRepeatType(PlaybackService.REPEAT_ALL);
                break;
            case PlaybackService.REPEAT_ALL:
                mService.setRepeatType(PlaybackService.REPEAT_ONE);
                break;
            default:
            case PlaybackService.REPEAT_ONE:
                mService.setRepeatType(PlaybackService.REPEAT_NONE);
                break;
        }
        update();
    }

    public void onPlaylistSwitchClick(View view) {
        boolean showCover = mBinding.songsList.getVisibility() == View.VISIBLE;
        if (playlistConstraintSet != null) {
            TransitionManager.beginDelayedTransition(mBinding.contentLayout);
            ConstraintSet cs = showCover ? coverConstraintSet : playlistConstraintSet;
            cs.applyTo(mBinding.contentLayout);
            mBinding.playlistSwitch.setImageResource(UiTools.getResourceFromAttribute(view.getContext(),
                    showCover ? R.attr.ic_playlist : R.attr.ic_playlist_on));
        } else {
            mBinding.songsList.setVisibility(showCover ? View.GONE : View.VISIBLE);
            mBinding.coverMediaSwitcher.setVisibility(showCover ? View.VISIBLE : View.GONE);
        }
    }

    public void onShuffleClick(View view) {
        if (mService != null)
            mService.shuffle();
        update();
    }

    public void onResumeToVideoClick(View v) {
        if (mService != null && mService.hasMedia()) {
            mService.getCurrentMediaWrapper().removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
            mService.switchToVideo();
        }
    }


    public void showAdvancedOptions(View v) {
        if (!isVisible())
            return;
        FragmentManager fm = getActivity().getSupportFragmentManager();
        AdvOptionsDialog advOptionsDialog = new AdvOptionsDialog();
        Bundle args = new Bundle();
        args.putInt(AdvOptionsDialog.MODE_KEY, AdvOptionsDialog.MODE_AUDIO);
        advOptionsDialog.setArguments(args);
        advOptionsDialog.show(fm, "fragment_adv_options");
    }

    public void show() {
        AudioPlayerContainerActivity activity = (AudioPlayerContainerActivity)getActivity();
        if (activity != null && activity.isAudioPlayerReady())
            activity.showAudioPlayer();
    }

    public void hide() {
        AudioPlayerContainerActivity activity = (AudioPlayerContainerActivity)getActivity();
        if (activity != null)
            activity.hideAudioPlayer();
    }

    public void setHeaderVisibilities(boolean advFuncVisible, boolean playlistSwitchVisible,
                                      boolean headerPlayPauseVisible, boolean progressBarVisible,
                                      boolean headerTimeVisible, boolean searchVisible) {
        mAdvFuncVisible = advFuncVisible;
        mPlaylistSwitchVisible = playlistSwitchVisible;
        mHeaderPlayPauseVisible = headerPlayPauseVisible;
        mProgressBarVisible = progressBarVisible;
        mHeaderTimeVisible = headerTimeVisible;
        mSearchVisible = searchVisible;
        restoreHeaderButtonVisibilities();
    }

    private void restoreHeaderButtonVisibilities() {
        mBinding.advFunction.setVisibility(mAdvFuncVisible ? View.VISIBLE : View.GONE);
        mBinding.playlistSwitch.setVisibility(mPlaylistSwitchVisible ? View.VISIBLE : View.GONE);
        mBinding.playlistSearch.setVisibility(mSearchVisible ? View.VISIBLE : View.GONE);
        mBinding.headerPlayPause.setVisibility(mHeaderPlayPauseVisible ? View.VISIBLE : View.GONE);
        mBinding.progressBar.setVisibility(mProgressBarVisible ? View.VISIBLE : View.GONE);
        mBinding.headerTime.setVisibility(mHeaderTimeVisible ? View.VISIBLE : View.GONE);
    }

    private void hideHeaderButtons() {
        mBinding.advFunction.setVisibility(View.GONE);
        mBinding.playlistSwitch.setVisibility(View.GONE);
        mBinding.playlistSearch.setVisibility(View.GONE);
        mBinding.headerPlayPause.setVisibility(View.GONE);
        mBinding.progressBar.setVisibility(View.GONE);
        mBinding.headerTime.setVisibility(View.GONE);
    }

    private final AudioMediaSwitcherListener mHeaderMediaSwitcherListener = new AudioMediaSwitcherListener() {

        @Override
        public void onMediaSwitching() {}

        @Override
        public void onMediaSwitched(int position) {
            if (mService == null)
                return;
            if (position == AudioMediaSwitcherListener.PREVIOUS_MEDIA)
                mService.previous(true);
            else if (position == AudioMediaSwitcherListener.NEXT_MEDIA)
                mService.next();
        }

        @Override
        public void onTouchDown() {
            hideHeaderButtons();
        }

        @Override
        public void onTouchUp() {
            restoreHeaderButtonVisibilities();
        }

        @Override
        public void onTouchClick() {
            AudioPlayerContainerActivity activity = (AudioPlayerContainerActivity)getActivity();
            activity.slideUpOrDownAudioPlayer();
        }
    };

    private final AudioMediaSwitcherListener mCoverMediaSwitcherListener = new AudioMediaSwitcherListener() {

        @Override
        public void onMediaSwitching() {}

        @Override
        public void onMediaSwitched(int position) {
            if (mService == null)
                return;
            if (position == AudioMediaSwitcherListener.PREVIOUS_MEDIA)
                mService.previous(true);
            else if (position == AudioMediaSwitcherListener.NEXT_MEDIA)
                mService.next();
        }

        @Override
        public void onTouchDown() {}

        @Override
        public void onTouchUp() {}

        @Override
        public void onTouchClick() {}
    };

    public void onSearchClick(View v) {
        mBinding.playlistSearch.setVisibility(View.GONE);
        mBinding.playlistSearchText.setVisibility(View.VISIBLE);
        if (mBinding.playlistSearchText.getEditText() != null)
            mBinding.playlistSearchText.getEditText().requestFocus();
        InputMethodManager imm = (InputMethodManager) VLCApplication.getAppContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(mBinding.playlistSearchText.getEditText(), InputMethodManager.SHOW_IMPLICIT);
        mHandler.postDelayed(hideSearchRunnable, SEARCH_TIMEOUT_MILLIS);
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int start, int before, int count) {}

    public boolean clearSearch() {
        mPlaylistAdapter.restoreList();
        return hideSearchField();
    }

    public boolean hideSearchField() {
        if (mBinding.playlistSearchText.getVisibility() != View.VISIBLE)
            return false;
        if (mBinding.playlistSearchText.getEditText() != null) {
            mBinding.playlistSearchText.getEditText().removeTextChangedListener(this);
            mBinding.playlistSearchText.getEditText().setText("");
            mBinding.playlistSearchText.getEditText().addTextChangedListener(this);
        }
        UiTools.setKeyboardVisibility(mBinding.playlistSearchText, false);
        mBinding.playlistSearch.setVisibility(View.VISIBLE);
        mBinding.playlistSearchText.setVisibility(View.GONE);
        return true;
    }

    Runnable hideSearchRunnable = new Runnable() {
        @Override
        public void run() {
            hideSearchField();
            mPlaylistAdapter.restoreList();
        }
    };

    @Override
    public void onTextChanged(CharSequence charSequence,  int start, int before, int count) {
        int length = charSequence.length();
        if (length > 1) {
            mPlaylistAdapter.getFilter().filter(charSequence);
            mHandler.removeCallbacks(hideSearchRunnable);
        } else if (length == 0) {
            mPlaylistAdapter.restoreList();
            hideSearchField();
        }
    }

    @Override
    public void afterTextChanged(Editable editable) {}

    @Override
    public void onConnected(PlaybackService service) {
        super.onConnected(service);
        mService.addCallback(this);
        mPlaylistAdapter.setService(service);
        update();
    }

    @Override
    public void onStop() {
        /* unregister before super.onStop() since mService is set to null from this call */
        if (mService != null)
            mService.removeCallback(this);
        super.onStop();
    }

    private class LongSeekListener implements View.OnTouchListener {
        boolean forward;
        int normal, pressed;
        long length;

        private LongSeekListener(boolean forwards, int normalRes, int pressedRes) {
            this.forward = forwards;
            this.normal = normalRes;
            this.pressed = pressedRes;
            this.length = -1;
        }

        int possibleSeek;
        boolean vibrated;

        @RequiresPermission(Manifest.permission.VIBRATE)
        Runnable seekRunnable = new Runnable() {
            @Override
            public void run() {
                if(!vibrated) {
                    ((android.os.Vibrator) VLCApplication.getAppContext().getSystemService(Context.VIBRATOR_SERVICE))
                            .vibrate(80);
                    vibrated = true;
                }

                if(forward) {
                    if(length <= 0 || possibleSeek < length)
                        possibleSeek += 4000;
                } else {
                    if(possibleSeek > 4000)
                        possibleSeek -= 4000;
                    else if(possibleSeek <= 4000)
                        possibleSeek = 0;
                }

                mBinding.time.setText(Tools.millisToString(mShowRemainingTime ? possibleSeek-length : possibleSeek));
                mBinding.timeline.setProgress(possibleSeek);
                mBinding.progressBar.setProgress(possibleSeek);
                mHandler.postDelayed(seekRunnable, 50);
            }
        };

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (mService == null)
                return false;
            switch(event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    (forward ? mBinding.next : mBinding.previous).setImageResource(this.pressed);

                    possibleSeek = (int) mService.getTime();

                    mPreviewingSeek = true;
                    vibrated = false;
                    length = mService.getLength();

                    mHandler.postDelayed(seekRunnable, 1000);
                    return true;

                case MotionEvent.ACTION_UP:
                    (forward ? mBinding.next : mBinding.previous).setImageResource(this.normal);
                    mHandler.removeCallbacks(seekRunnable);
                    mPreviewingSeek = false;

                    if(event.getEventTime()-event.getDownTime() < 1000) {
                        if(forward)
                            onNextClick(v);
                        else
                            //onTrim(v);
                            onPreviousClick(v);
                    } else {
                        if(forward) {
                            if(possibleSeek < mService.getLength())
                                mService.setTime(possibleSeek);
                            else
                                onNextClick(v);
                        } else {
                            if(possibleSeek > 0)
                                mService.setTime(possibleSeek);
                            else
                                //onTrim(v);
                                onPreviousClick(v);
                        }
                    }
                    return true;
            }
            return false;
        }
    }

    public void showPlaylistTips() {
        AudioPlayerContainerActivity activity = (AudioPlayerContainerActivity)getActivity();
        if (activity != null)
            activity.showTipViewIfNeeded(R.id.audio_playlist_tips, PREF_PLAYLIST_TIPS_SHOWN);
    }

    public void showAudioPlayerTips() {
        AudioPlayerContainerActivity activity = (AudioPlayerContainerActivity)getActivity();
        if (activity != null)
            activity.showTipViewIfNeeded(R.id.audio_player_tips, PREF_AUDIOPLAYER_TIPS_SHOWN);
    }

    public void onStateChanged(int newState) {
        mPlayerState = newState;
        switch (newState) {
            case BottomSheetBehavior.STATE_COLLAPSED:
                mBinding.header.setBackgroundResource(DEFAULT_BACKGROUND_DARKER_ID);
                setHeaderVisibilities(false, false, true, true, true, false);
                break;
            case BottomSheetBehavior.STATE_EXPANDED:
                mBinding.header.setBackgroundResource(0);
                setHeaderVisibilities(true, true, false, false, false, true);
                showPlaylistTips();
                if (mService != null)
                    mPlaylistAdapter.setCurrentIndex(mService.getCurrentMediaPosition());
                break;
            default:
                mBinding.header.setBackgroundResource(0);
        }
    }

    /*
     * Override this method to prefent NPE on mFragmentManager reference.
     */
    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        if (getFragmentManager() != null)
            super.setUserVisibleHint(isVisibleToUser);
    }

    static final int UPDATE = 0;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE:
                    doUpdate();
                default:
                    super.handleMessage(msg);
            }
        }
    };
}
