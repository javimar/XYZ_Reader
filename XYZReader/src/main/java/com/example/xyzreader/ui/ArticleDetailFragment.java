package com.example.xyzreader.ui;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;

import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ShareCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * A fragment representing a single Article detail screen. This fragment is
 * either contained in a {@link ArticleListActivity} in two-pane mode (on
 * tablets) or a {@link ArticleDetailActivity} on handsets.
 */
@SuppressLint("SimpleDateFormat")
public class ArticleDetailFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor>
{
    private static final String TAG = "ArticleDetailFragment";

    public static final String ARG_ITEM_ID = "item_id";

    private Cursor mCursor;
    private long mItemId;
    private View mRootView;
    private int mMutedColor = 0xFF333333;

    private ColorDrawable mStatusBarColorDrawable;

    private int mTopInset;
    private int mScrollY;
    private boolean mIsCard = false;
    private int mStatusBarFullOpacityBottom;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss");
    // Use default locale format
    private SimpleDateFormat outputFormat = new SimpleDateFormat();
    // Most time functions can only handle 1902 - 2037
    private GregorianCalendar START_OF_EPOCH = new GregorianCalendar(2,1,1);

    @BindView(R.id.article_title) TextView mTitleView;
    @BindView(R.id.article_byline) TextView mBylineView;
    @BindView(R.id.article_body) TextView mBodyView;
    @BindView(R.id.share_fab) FloatingActionButton mShareFab;
    @BindView(R.id.toolbar_detail) Toolbar mToolbarDetail;
    @BindView(R.id.img_book_toolbar) ImageView mImgBookToolbar;
    @BindView(R.id.collapse_toolbar_detail) CollapsingToolbarLayout mCollapsingToolbarLayout;
    @BindView(R.id.meta_bar) View mMetaBar;
    @BindView(R.id.photo_container) View mPhotoContainerView;

    private int mVibrant, mDarkVibrant = 0;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ArticleDetailFragment() {
    }

    public static ArticleDetailFragment newInstance(long itemId)
    {
        Bundle arguments = new Bundle();
        arguments.putLong(ARG_ITEM_ID, itemId);
        ArticleDetailFragment fragment = new ArticleDetailFragment();
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        if (getArguments().containsKey(ARG_ITEM_ID)) {
            mItemId = getArguments().getLong(ARG_ITEM_ID);
        }

        mIsCard = getResources().getBoolean(R.bool.detail_is_card);
        mStatusBarFullOpacityBottom = getResources().getDimensionPixelSize(
                R.dimen.detail_card_top_margin);
        setHasOptionsMenu(true);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState)
    {
        mRootView = inflater.inflate(R.layout.fragment_article_detail_include, container, false);
        ButterKnife.bind(this, mRootView);

        mPhotoContainerView.setVisibility(View.GONE);
        mStatusBarColorDrawable = new ColorDrawable(0);

        // Toolbar logic
        ((AppCompatActivity)getActivity()).setSupportActionBar(mToolbarDetail);
        ((AppCompatActivity)getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        mCollapsingToolbarLayout.setTitleEnabled(false);

        //FAB
        mShareFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                startActivity(Intent.createChooser(ShareCompat.IntentBuilder
                        .from(getActivity())
                        .setType("text/plain")
                        .setText("Some sample text")
                        .getIntent(), getString(R.string.action_share)));
            }
        });

        // meta bar needs to collapse correctly with height of Toolbar
        mMetaBar.getViewTreeObserver().addOnGlobalLayoutListener
                (new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout()
            {
                int metaBarHeight = mMetaBar.getMeasuredHeight();
                if(metaBarHeight > 0) {
                    mMetaBar.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    // set toolbar height to accomodate the meta_bar at run time
                    mToolbarDetail.setMinimumHeight(metaBarHeight);
                }
            }
        });

        bindViews();
        updateStatusBar();
        return mRootView;
    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        // In support library r8, calling initLoader for a fragment in a FragmentPagerAdapter in
        // the fragment's onCreate may cause the same LoaderManager to be dealt to multiple
        // fragments because their mIndex is -1 (haven't been added to the activity yet). Thus,
        // we do this in onActivityCreated.
        getLoaderManager().initLoader(0, null, this);
    }


    private void bindViews()
    {
        if (mRootView == null) {
            return;
        }

        mBylineView.setMovementMethod(new LinkMovementMethod());
        mBodyView.setTypeface(Typeface.createFromAsset(getResources().getAssets(),
                "Rosario-Regular.ttf"));

        if (mCursor != null)
        {
            mRootView.setAlpha(0);
            mRootView.setVisibility(View.VISIBLE);
            mRootView.animate().alpha(1);
            mTitleView.setText(mCursor.getString(ArticleLoader.Query.TITLE));
            Date publishedDate = parsePublishedDate();
            if (!publishedDate.before(START_OF_EPOCH.getTime())) {
                mBylineView.setText(Html.fromHtml(
                        DateUtils.getRelativeTimeSpanString(
                                publishedDate.getTime(),
                                System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_ALL).toString()
                                + " by <font color='#ffffff'>"
                                + mCursor.getString(ArticleLoader.Query.AUTHOR)
                                + "</font>"));
            }
            else
            {
                // If date is before 1902, just show the string
                mBylineView.setText(Html.fromHtml(
                        outputFormat.format(publishedDate) + " by <font color='#ffffff'>"
                        + mCursor.getString(ArticleLoader.Query.AUTHOR)
                                + "</font>"));
            }
            mBodyView.setText(Html.fromHtml(mCursor.getString(ArticleLoader.Query.BODY)
                    .replaceAll("(\r\n|\n)", "<br />")));

            ImageLoaderHelper.getInstance(getActivity()).getImageLoader()
                    .get(mCursor.getString(ArticleLoader.Query.PHOTO_URL),
                        new ImageLoader.ImageListener()
                        {
                            @Override
                            public void onResponse(ImageLoader.ImageContainer imageContainer,
                                                   boolean b)
                            {
                                Bitmap bitmap = imageContainer.getBitmap();
                                if (bitmap != null)
                                {
                                    // generate palette on the main thread :-/
                                    Palette palette = Palette.from(bitmap).generate();
                                    Palette.Swatch vibrantP = palette.getVibrantSwatch();
                                    Palette.Swatch darkVibrantP = palette.getDarkVibrantSwatch();
                                    if(vibrantP != null && darkVibrantP != null)
                                    {
                                        mVibrant = vibrantP.getRgb();
                                        mDarkVibrant = darkVibrantP.getRgb();
                                        mMetaBar.setBackgroundColor(mVibrant);

                                        setCollapsingBarColor();
                                    }
                                    // place image in the AppBar
                                    mImgBookToolbar.setImageBitmap(imageContainer.getBitmap());
                                    updateStatusBar();
                                }
                            }
                            @Override
                            public void onErrorResponse(VolleyError volleyError) {
                            }
                        });
        }
        else
        {
            mRootView.setVisibility(View.GONE);
            mTitleView.setText("N/A");
            mBylineView.setText("N/A" );
            mBodyView.setText("N/A");
        }
    }


    private void setCollapsingBarColor()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            if(getActivity() != null)
            {
                Window window = getActivity().getWindow();
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                window.setStatusBarColor(mDarkVibrant);
            }
        }
        mCollapsingToolbarLayout.setContentScrimColor(mVibrant);
        mCollapsingToolbarLayout.setStatusBarScrimColor(mDarkVibrant);
    }


    // LOADER LOGIC
    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle)
    {
        return ArticleLoader.newInstanceForItemId(getActivity(), mItemId);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor)
    {
        if (!isAdded()) {
            if (cursor != null) {
                cursor.close();
            }
            return;
        }

        mCursor = cursor;
        if (mCursor != null && !mCursor.moveToFirst())
        {
            Log.e(TAG, "Error reading item detail cursor");
            mCursor.close();
            mCursor = null;
        }

        bindViews();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader)
    {
        mCursor = null;
        bindViews();
    }


    private void updateStatusBar()
    {
        int color = 0;
        if (mTopInset != 0 && mScrollY > 0)
        {
            float f = progress(mScrollY,
                    mStatusBarFullOpacityBottom - mTopInset * 3,
                    mStatusBarFullOpacityBottom - mTopInset);
            color = Color.argb((int) (255 * f),
                    (int) (Color.red(mMutedColor) * 0.9),
                    (int) (Color.green(mMutedColor) * 0.9),
                    (int) (Color.blue(mMutedColor) * 0.9));
        }
        mStatusBarColorDrawable.setColor(color);
    }

    static float progress(float v, float min, float max)
    {
        return constrain((v - min) / (max - min), 0, 1);
    }

    static float constrain(float val, float min, float max)
    {
        if (val < min) {
            return min;
        } else if (val > max) {
            return max;
        } else {
            return val;
        }
    }


    public int getUpButtonFloor()
    {
        if (mPhotoContainerView == null || mImgBookToolbar.getHeight() == 0)
        {
            return Integer.MAX_VALUE;
        }

        // account for parallax
        return mIsCard
                ? (int) mPhotoContainerView.getTranslationY() +
                mImgBookToolbar.getHeight() - mScrollY
                : mImgBookToolbar.getHeight() - mScrollY;
    }


    private Date parsePublishedDate()
    {
        try {
            String date = mCursor.getString(ArticleLoader.Query.PUBLISHED_DATE);
            return dateFormat.parse(date);
        }
        catch (ParseException ex) {
            Log.e(TAG, ex.getMessage());
            Log.i(TAG, "passing today's date");
            return new Date();
        }
    }

}
