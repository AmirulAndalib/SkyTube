/*
 * SkyTube
 * Copyright (C) 2016  Ramon Mifsud
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation (version 3 of the License).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package free.rm.skytube.gui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import free.rm.skytube.R;
import free.rm.skytube.app.EventBus;
import free.rm.skytube.businessobjects.Logger;
import free.rm.skytube.businessobjects.YouTube.POJOs.YouTubeChannel;
import free.rm.skytube.businessobjects.YouTube.newpipe.ChannelId;
import free.rm.skytube.businessobjects.db.DatabaseTasks;
import free.rm.skytube.databinding.FragmentChannelBrowserBinding;
import free.rm.skytube.gui.businessobjects.fragments.FragmentEx;
import free.rm.skytube.gui.businessobjects.fragments.TabFragment;
import free.rm.skytube.gui.businessobjects.views.ChannelSubscriber;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * A Fragment that displays information about a channel.
 *
 * This fragment is made up of three other fragments:
 * <ul>
 *     <li>{@link ChannelVideosFragment}</li>
 *     <li>{@link ChannelPlaylistsFragment}.</li>
 *     <li>{@link ChannelAboutFragment}.</li>
 * </ul>
 */
public class ChannelBrowserFragment extends FragmentEx implements ChannelSubscriber {

	private YouTubeChannel		channel;
	private ChannelId channelId;
	private Boolean 			userSubscribed;

	public static final String FRAGMENT_CHANNEL_VIDEOS = "ChannelBrowserFragment.FRAGMENT_CHANNEL_VIDEOS";
	public static final String FRAGMENT_CHANNEL_PLAYLISTS = "ChannelBrowserFragment.FRAGMENT_CHANNEL_PLAYLISTS";

	private FragmentChannelBrowserBinding binding;
	private CompositeDisposable          disposable = new CompositeDisposable();

	public static final String CHANNEL_OBJ = "ChannelBrowserFragment.ChannelObj";
	public static final String CHANNEL_ID  = "ChannelBrowserFragment.ChannelID";

	// The fragments that will be displayed
	private ChannelVideosFragment       channelVideosFragment;
	private ChannelPlaylistsFragment    channelPlaylistsFragment;
	private ChannelAboutFragment        channelAboutFragment;

	private ChannelPagerAdapter channelPagerAdapter;

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		if(savedInstanceState != null) {
			channelVideosFragment = (ChannelVideosFragment)getChildFragmentManager().getFragment(savedInstanceState, FRAGMENT_CHANNEL_VIDEOS);
			channelPlaylistsFragment = (ChannelPlaylistsFragment)getChildFragmentManager().getFragment(savedInstanceState, FRAGMENT_CHANNEL_PLAYLISTS);
		}

		// inflate the layout for this fragment
		binding = FragmentChannelBrowserBinding.inflate(inflater, container, false);

		binding.channelSubscribeButton.setIcon(new IconicsDrawable(getContext(),MaterialDesignIconic.Icon.gmi_favorite));

		binding.tabLayout.setupWithViewPager(binding.pager);
		binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
			@Override
			public void onTabSelected(TabLayout.Tab tab) {
				binding.pager.setCurrentItem(tab.getPosition());
			}

			@Override
			public void onTabUnselected(TabLayout.Tab tab) {
			}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                //When current tab reselected scroll to the top of the video list
                TabFragment tabFragment = channelPagerAdapter.getItem(tab.getPosition());
                if (tabFragment instanceof VideosGridFragment) {
                    ((VideosGridFragment)tabFragment).scrollToTop();
                }
            }
		});

		binding.pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
			@Override
			public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
			}

			@Override
			public void onPageSelected(int position) {
				if (channelPagerAdapter != null) {
					channelPagerAdapter.getItem(position).onFragmentSelected();
				}
			}

			@Override
			public void onPageScrollStateChanged(int state) {
			}
		});

		// setup the toolbar/actionbar
		setSupportActionBar(binding.toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		binding.channelSubscribeButton.setOnClickListener(view -> {
			if (userSubscribed != null && channel != null) {
				startAnimation(view);
				disposable.add(
					DatabaseTasks.subscribeToChannel(!userSubscribed, ChannelBrowserFragment.this, getContext(), channelId, true).subscribe(result -> {
						ViewCompat.animate(view).setDuration(200);
						view.setRotation(0);
					})
				);
			}
		});
		getChannelParameters();

		return binding.getRoot();
	}

	@Override
	public void onDestroy() {
		if (disposable != null) {
			disposable.dispose();
		}
		binding = null;
		super.onDestroy();
	}

	private static void startAnimation(View fab) {
		fab.setRotation(0);
		ViewCompat.animate(fab)
				.rotation(360)
				.withLayer()
				//.setDuration(1000)
				.setInterpolator(new AccelerateDecelerateInterpolator())
				.start();
	}

	@Override
	public void setSubscribedState(boolean subscribed) {
		final ExtendedFloatingActionButton channelSubscribeButton = binding.channelSubscribeButton;
		channelSubscribeButton.setVisibility(View.VISIBLE);
		userSubscribed = subscribed;
		if (subscribed) {
			channelSubscribeButton.setIcon(new IconicsDrawable(getContext(), MaterialDesignIconic.Icon.gmi_eye_off));
			channelSubscribeButton.setText(R.string.unsubscribe);
		} else {
			channelSubscribeButton.setIcon(new IconicsDrawable(getContext(), MaterialDesignIconic.Icon.gmi_eye));
			channelSubscribeButton.setText(R.string.subscribe);
		}
	}

	private void setChannel(YouTubeChannel channel) {
		this.channel = channel;
		if (channel != null) {
			this.channelId = channel.getChannelId();
		} else {
			this.channelId = null;
		}
	}

	private void getChannelParameters() {
		// we need to create a YouTubeChannel object:  this can be done by either:
		//   (1) the YouTubeChannel object is passed to this Fragment
		//   (2) passing the channel ID... a task is then created to create a YouTubeChannel
		//       instance using the given channel ID
		final Bundle bundle = getArguments();
		final ChannelId oldChannelId = this.channelId;

		Logger.i(ChannelBrowserFragment.this, "getChannelParameters " + bundle);
		if (bundle != null  &&  bundle.getSerializable(CHANNEL_OBJ) != null) {
			setChannel((YouTubeChannel) bundle.getSerializable(CHANNEL_OBJ));
		} else {
			channelId = new ChannelId(bundle.getString(CHANNEL_ID));
			if (!Objects.equals(oldChannelId, channelId)) {
				this.channel = null;
			}
		}
		if (channel == null) {
			disposable.add(DatabaseTasks.getChannelInfo(requireContext(), channelId, false)
				.subscribe(youTubeChannel -> {
					if (youTubeChannel == null) {
						return;
					}
					// In the event this fragment is passed a channel id and not a channel object, set the
					// channel the subscribe button is for since there wasn't a channel object to set when
					// the button was created.
					channel = youTubeChannel.channel();
					initViews();
				}));
		} else {
			initViews();
		}
	}

	@Override
	public synchronized void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(CHANNEL_ID, channelId.getRawId());
		// if channel is not null, the ChannelPagerAdapter is initialized, with all the sub-fragments
		if (channel != null) {
			outState.putSerializable(CHANNEL_OBJ, channel);
			if (channelVideosFragment != null) {
				getChildFragmentManager().putFragment(outState, FRAGMENT_CHANNEL_VIDEOS, channelVideosFragment);
			}
			if (channelPlaylistsFragment != null) {
				getChildFragmentManager().putFragment(outState, FRAGMENT_CHANNEL_PLAYLISTS, channelPlaylistsFragment);
			}
		}
	}

	private FragmentManager getChildFragmentManagerSafely() {
		try {
			return getChildFragmentManager();
		} catch (IllegalStateException e) {
			Logger.e(this, "Fragment mapper is not available, as the Fragment is not attached :"+e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Initialise views that are related to {@link #channel}.
	 */
	private synchronized void initViews() {
		if (channel != null) {
			FragmentManager fm = getChildFragmentManagerSafely();
			if (fm == null) {
				return;
			}
			channelPagerAdapter = new ChannelPagerAdapter(fm);
			binding.pager.setOffscreenPageLimit(2);
			binding.pager.setAdapter(channelPagerAdapter);

			this.channelVideosFragment.setYouTubeChannel(channel);

			this.channelVideosFragment.onFragmentSelected();

			Glide.with(requireContext())
					.load(channel.getThumbnailUrl())
					.apply(new RequestOptions().placeholder(R.drawable.channel_thumbnail_default))
					.into(binding.channelThumbnailImageView);

			Glide.with(requireContext())
					.load(channel.getBannerUrl())
					.apply(new RequestOptions().placeholder(R.drawable.banner_default))
					.into(binding.channelBannerImageView);

			if (channel.getSubscriberCount() >= 0) {
				binding.channelSubsTextView.setText(channel.getTotalSubscribers());
			} else {
				Logger.i(this, "Channel subscriber count for %s is %s", channel.getTitle(), channel.getSubscriberCount());
				binding.channelSubsTextView.setVisibility(View.GONE);
			}

			ActionBar actionBar = getSupportActionBar();
			if (actionBar != null) {
				actionBar.setTitle(channel.getTitle());
			}

			// if the user has subscribed to this channel, then change the state of the
			// subscribe button
			setSubscribedState(channel.isUserSubscribed());

			if (userSubscribed) {
				// the user is visiting the channel, so we need to update the last visit time
				channel.updateLastVisitTime();

				// since we are visiting the channel, then we need to disable the new videos notification
				EventBus.getInstance().notifyChannelNewVideosStatus(channel.getChannelId(), false);
			}
		}
	}


	////////////////////////////////////////////////////////////////////////////////////////////////
	private class ChannelPagerAdapter extends FragmentPagerAdapter {
		/** List of fragments that will be displayed as tabs. */
		private final List<TabFragment> channelBrowserFragmentList = new ArrayList<>();

		public ChannelPagerAdapter(FragmentManager fm) {
			super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);

			// Initialize fragments
			if (channelVideosFragment == null) {
				channelVideosFragment = new ChannelVideosFragment();
			}

			if (channelPlaylistsFragment == null) {
				channelPlaylistsFragment = new ChannelPlaylistsFragment();
			}

			if (channelAboutFragment == null) {
				channelAboutFragment = new ChannelAboutFragment();
			}

			Bundle bundle = new Bundle();
			bundle.putSerializable(CHANNEL_OBJ, channel);

			channelVideosFragment.setArguments(bundle);
			channelPlaylistsFragment.setArguments(bundle);
			channelAboutFragment.setArguments(bundle);

			channelBrowserFragmentList.add(channelVideosFragment);
			channelBrowserFragmentList.add(channelPlaylistsFragment);
			channelBrowserFragmentList.add(channelAboutFragment);
		}

		@Override
		public TabFragment getItem(int position) {
			return channelBrowserFragmentList.get(position);
		}

		@Override
		public int getCount() {
			return channelBrowserFragmentList.size();
		}

		@Override
		public CharSequence getPageTitle(int position) {
			return channelBrowserFragmentList.get(position).getFragmentName();
		}
	}

	/**
	 * Return the Channel Playlists Fragment. This is needed so that the fragment can have a reference to MainActivity
	 * @return {@link free.rm.skytube.gui.fragments.ChannelPlaylistsFragment}
	 */
	public ChannelPlaylistsFragment getChannelPlaylistsFragment() {
		if(channelPlaylistsFragment == null)
			channelPlaylistsFragment = new ChannelPlaylistsFragment();
		return channelPlaylistsFragment;
	}
}
