package meta11ica.tn.twitchvod

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.app.HeadersSupportFragment.OnHeaderClickedListener
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowHeaderPresenter
import androidx.leanback.widget.RowPresenter
import com.example.twitchvod.EditFavouriteListFragment
import org.json.JSONArray
import java.io.File
import java.util.Timer
import kotlin.math.max
import kotlin.math.min


/**
 * Loads a grid of cards with movies to browse.
 */
class MainFragment : BrowseSupportFragment() {

    private lateinit var mBackgroundManager: BackgroundManager
    private var mDefaultBackground: Drawable? = null
    private lateinit var mMetrics: DisplayMetrics
    private var mBackgroundTimer: Timer? = null
    private lateinit var sharedPrefs: SharedPreferences
    private var historyLength = 0
    private var extraHeadersIndex = 0
    private var headerIndex = 0

    // Register a BroadcastReceiver to listen for SharedPreferences changes
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.SHARED_PREF_CHANGED") {
                // Update UI or take necessary action
                updateWatchList()
                rowsAdapter.notifyArrayItemRangeChanged(0, rowsAdapter.size())
                rowsAdapter.notifyArrayItemRangeChanged(0, rowsAdapter.size())
            }
        }
    }


    private val listRowStreamContinueWatchAdapter = ArrayObjectAdapter(StreamPresenter())
    val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        Log.i(TAG, "onCreate")
        super.onActivityCreated(savedInstanceState)

        prepareBackgroundManager()
        historyLength = resources.getInteger(R.integer.maximum_history_size)


        setupUIElements()
        loadRows()
        setupEventListeners()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        return view
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Register the BroadcastReceiver in onViewCreated
        val filter = IntentFilter("com.example.SHARED_PREF_CHANGED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireActivity().registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireActivity().registerReceiver(receiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: " + mBackgroundTimer?.toString())
        mBackgroundTimer?.cancel()
    }

    private fun prepareBackgroundManager() {

        mBackgroundManager = BackgroundManager.getInstance(activity)
        mBackgroundManager.attach(requireActivity().window)
        mDefaultBackground = ContextCompat.getDrawable(requireActivity(), R.drawable.default_background)
        mMetrics = DisplayMetrics()
        requireActivity().windowManager.defaultDisplay.getMetrics(mMetrics)
    }

    private fun setupUIElements() {
        title = getString(R.string.browse_title)
        // over title
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true

        // set fastLane (or headers) background color
        brandColor = ContextCompat.getColor(requireActivity(), R.color.fastlane_background)
        // set search icon color
        searchAffordanceColor = ContextCompat.getColor(requireActivity(), R.color.search_opaque)
    }

    private fun loadRows() {

        sharedPrefs = requireActivity().getSharedPreferences("Streamers",  0)!!


        try {
            updateWatchList()

            val header = HeaderItem(
                headerIndex.toLong(),
                resources.getString(R.string.header_continue_watching)
            )
            rowsAdapter.add(ListRow(header, listRowStreamContinueWatchAdapter))
            headerIndex++
            extraHeadersIndex++

        } catch(e : Exception)
        {
            Toast.makeText(requireActivity(), "Problem loading history, please erase the history.", Toast.LENGTH_SHORT)
                .show()
        }
        /*
        val refreshHeader = HeaderItem(
            headerIndex.toLong(),
            "Refresh"
        )
        rowsAdapter.add(ListRow(refreshHeader, ArrayObjectAdapter()))
        headerIndex++
        extraHeadersIndex++
        */

        val list = JSONArray(sharedPrefs.getString("movies","empty"))
        val cardPresenter = StreamPresenter()
        for (i in 0 until list.length()) {
            val streamer = list.getJSONObject(i)
            val listRowAdapter = ArrayObjectAdapter(cardPresenter)
            for (j in 0 until min(NUM_COLS,JSONArray(streamer.getString("streams")).length())) {
                val stream = JSONArray(streamer.getString("streams")).getJSONObject(j)
                val movie = Movie()
                movie.id = stream?.getLong("id")!!
                movie.duration = stream.getLong("duration")
                movie.title = stream.getString("title")
                movie.description = stream.getString("description")
                movie.studio = stream.getString("studio")
                movie.cardImageUrl = stream.getString("cardImageUrl")
                movie.backgroundImageUrl = stream.getString("bgImageUrl")
                movie.videoUrl = stream.getString("videoUrl")

                listRowAdapter.add(movie)
            }
            val header = HeaderItem(headerIndex.toLong(), streamer.getString("streamer_id"))
            rowsAdapter.add(ListRow(header, listRowAdapter))
            headerIndex++
        }

        val gridHeader = HeaderItem(NUM_ROWS.toLong(), "PREFERENCES")

        val mGridPresenter = GridItemPresenter()
        val gridRowAdapter = ArrayObjectAdapter(mGridPresenter)
        gridRowAdapter.add(resources.getString(R.string.update_channel_name))
        gridRowAdapter.add(resources.getString(R.string.edit_favourite_list))
        gridRowAdapter.add(resources.getString(R.string.clear_watch_history))
        gridRowAdapter.add(resources.getString(R.string.refresh_live))
        rowsAdapter.add(ListRow(gridHeader, gridRowAdapter))

        adapter = rowsAdapter

    }

    private fun setupEventListeners() {
        setOnSearchClickedListener {
            resetBackground(activity)

            activity?.supportFragmentManager?.beginTransaction()
                ?.replace(R.id.main_browse_fragment, SearchFragment())
                ?.commit()
        }
        val headersFragment = headersSupportFragment

        headersFragment.setOnHeaderClickedListener(HeaderClickedListener())
        onItemViewClickedListener = ItemViewClickedListener()
    }

    private inner class HeaderClickedListener : OnHeaderClickedListener {
        override fun onHeaderClicked(viewHolder: RowHeaderPresenter.ViewHolder?, row: Row?) {
            if (row != null) {
                val selectedRow = row.headerItem.name
                if(selectedRow=="Refresh") {
                    val intent = Intent(
                        this@MainFragment.requireActivity(),
                        SplashActivity::class.java
                    )
                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                    startActivity(intent)
                    this@MainFragment.requireActivity().finish()
                }
                else {
                    startHeadersTransition(false)
                }
            }
        }

    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder,
            item: Any,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row
        ) {

            if (item is Movie) {

                if (row.id.toInt() == 0) {
                    val intent = Intent(activity!!, PlaybackActivity::class.java)
                    intent.putExtra(PlaybackActivity.CONFIRMATION_PROMPT, false)
                    intent.putExtra(PlaybackActivity.MOVIE, item)
                    startActivity(intent)
                } else {

                    val intent = Intent(activity!!, DetailsActivity::class.java)
                    intent.putExtra(DetailsActivity.MOVIE, item)
                    intent.putExtra(DetailsActivity.ROW, row.id - extraHeadersIndex)

                    val bundle = (itemViewHolder.view as CustomImageCardView).mainImage.let {
                        ActivityOptionsCompat.makeSceneTransitionAnimation(
                            activity!!,
                            it,
                            DetailsActivity.SHARED_ELEMENT_NAME
                        )
                            .toBundle()
                    }
                    startActivity(intent, bundle)
                }
            } else if (item is String) {
                if (item.contains(getString(R.string.edit_favourite_list))) {
                    resetBackground(activity)
                    activity?.supportFragmentManager?.beginTransaction()
                        ?.replace(R.id.main_browse_fragment, EditFavouriteListFragment())
                        ?.commit()
                }
                if (item.contains(getString(R.string.refresh_live))) {
                    val intent = Intent(
                        requireActivity(),
                        SplashActivity::class.java
                    )
                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                    startActivity(intent)
                    requireActivity().finish()
                }
                else if (item.contains(getString(R.string.clear_watch_history))) {
                    val watchList = if (sharedPrefs.contains("watchList")) JSONArray(
                        sharedPrefs.getString(
                            "watchList",
                            ""
                        )
                    ) else JSONArray()
                    val editor = sharedPrefs.edit()
                    for (i in 0 until watchList.length()) {
                        val streamId = Movie.stringToMovie(watchList.getString(i)).id.toString()

                        if (sharedPrefs.contains(streamId)) editor.remove(streamId)
                        if (sharedPrefs.contains("$streamId-progress")) editor.remove("$streamId-progress")

                    }
                    editor.remove("watchList")
                    editor.apply()
                    val intent = Intent("com.example.SHARED_PREF_CHANGED")
                    activity?.sendBroadcast(intent)
                    Toast.makeText(activity!!, R.string.success_clear_history, Toast.LENGTH_SHORT)
                        .show()
                }

                else if (item.contains(getString(R.string.update_channel_name))) {
                    val preferences = context?.getSharedPreferences(
                        context?.packageName + "_AutoUpdateApk",
                        Context.MODE_PRIVATE
                    )
                    val filename = preferences?.getString("update_file","nothing")
                    if(filename!="nothing") {
                        val url = context?.filesDir
                            ?.absolutePath + "/" + filename


                        try {
                            val file = File(url)
                            val uri = FileProvider.getUriForFile(
                                requireContext(),
                                "${requireContext().packageName}.provider",
                                file
                            )

                            val promptInstall = Intent(Intent.ACTION_VIEW)
                                .setDataAndType(uri, "application/vnd.android.package-archive")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                            context?.startActivity(Intent.createChooser(promptInstall, "Chooser"))
                        } catch (e: Exception) {
                            // Handle the exception
                            e.printStackTrace()
                            // Show a toast message or log the error
                            Toast.makeText(activity!!, R.string.error_installing_apk, Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                    else  {
                        Toast.makeText(activity!!, R.string.latest_version_already, Toast.LENGTH_SHORT)                                .show()


                    }
                }

            }
        }
    }




    private inner class GridItemPresenter : Presenter() {
        override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
            val view = TextView(parent.context)
            view.layoutParams = ViewGroup.LayoutParams(GRID_ITEM_WIDTH, GRID_ITEM_HEIGHT)
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.setBackgroundColor(ContextCompat.getColor(activity!!, R.color.default_background))
            view.setTextColor(Color.WHITE)
            view.gravity = Gravity.CENTER
            return ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
            (viewHolder.view as TextView).text = item as String
        }


        override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
    }

    fun resetBackground(activity: FragmentActivity?)
    {

        BackgroundManager.getInstance(activity)
        val backgroundDrawable: Drawable? = requireContext().getDrawable(R.drawable.default_background)
        val myView: View? = view?.rootView
        if (myView != null) {
            myView.background = backgroundDrawable
        }
    }

    fun updateWatchList() {
        listRowStreamContinueWatchAdapter.clear()
        val strExistingWatchList = sharedPrefs.getString("watchList", "")
        val streamWatchList: MutableList<Movie> = arrayListOf()

        if (!strExistingWatchList.isNullOrEmpty()) {
            val watchList = JSONArray(strExistingWatchList)
            //val historyLength = 99
            for (i in watchList.length() - 1 downTo max(0, watchList.length() - historyLength)) {
                val streamElement = Movie.stringToMovie(watchList.getString(i))
                streamElement.progress =
                    sharedPrefs.getInt("${streamElement.id}-progress", 0)

                streamWatchList.add(streamElement)
                listRowStreamContinueWatchAdapter.add(streamElement)
            }
            if (watchList.length() > historyLength) {
                val lastXObjects = getLastXObjects(watchList, historyLength)
                sharedPrefs.edit().putString("watchList", lastXObjects.toString()).apply()
            }
        }
        // Notify the adapter that the data set has changed
        listRowStreamContinueWatchAdapter.notifyArrayItemRangeChanged(0, listRowStreamContinueWatchAdapter.size())
    }

    private fun getLastXObjects(jsonArray: JSONArray, historyLength: Int): JSONArray {
        val length = jsonArray.length()
        val resultArray = JSONArray()

        val startIndex = length - historyLength
        val endIndex = if (startIndex < 0) 0 else startIndex

        for (i in endIndex until length) {
            val obj = jsonArray.getString(i)
            resultArray.put(obj)
        }
        return resultArray
    }

    companion object {
        private const val TAG = "MainFragment"

        private const val GRID_ITEM_WIDTH = 200
        private const val GRID_ITEM_HEIGHT = 200
        private const val NUM_ROWS = 6
        private const val NUM_COLS = 15
    }
}