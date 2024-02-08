package meta11ica.tn.twitchvod

import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.lifecycle.lifecycleScope
import khttp.post
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URL
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date

/** Handles video playback with media controls. */
class PlaybackVideoFragment : VideoSupportFragment() {

    private lateinit var mTransportControlGlue: BasicTransportControlsGlue

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val (_, title, description, _, _, videoUrl) =
            activity?.intent?.getSerializableExtra(DetailsActivity.MOVIE) as Movie
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()

        StrictMode.setThreadPolicy(policy)
        lifecycleScope.launch {
            var finalVideoUrl = videoUrl
            if (videoUrl != null) {
                if(!videoUrl.contains("api/channel/hls")) {
            val regex = Regex("""/videos/(\d+)""")
            val matchResult = videoUrl?.let { regex.find(it) }
            val vodId = matchResult?.groupValues?.get(1)

            val data = vodId?.let { fetchTwitchDataGQL(it) }
            val vodData = data?.getJSONObject("data")?.getJSONObject("video")
            val channelData = vodData?.getJSONObject("owner")
            val resolutions = mapOf(
                "160p30" to mapOf("res" to "284x160", "fps" to 30),
                "360p30" to mapOf("res" to "640x360", "fps" to 30),
                "480p30" to mapOf("res" to "854x480", "fps" to 30),
                "720p60" to mapOf("res" to "1280x720", "fps" to 60),
                "1080p60" to mapOf("res" to "1920x1080", "fps" to 60),
                "chunked" to mapOf("res" to "1920x1080", "fps" to 60)
            )

            val sortedDict = resolutions.keys.sortedDescending()

            val orderedResolutions = sortedDict.associateWith { resolutions[it] }

            val currentURL = URL(vodData?.getString("seekPreviewsURL"))

            val domain = currentURL.host
            val paths = currentURL.path.split("/")
            val vodSpecialID = paths[paths.indexOfFirst { it.contains("storyboards") } - 1]

            var fakePlaylist = """#EXTM3U
#EXT-X-TWITCH-INFO:ORIGIN="s3",B="false",REGION="EU",USER-IP="127.0.0.1",SERVING-ID="${createServingID()}",CLUSTER="cloudfront_vod",USER-COUNTRY="BE",MANIFEST-CLUSTER="cloudfront_vod""""

            //val now = Date("2023-02-10")
            val formatter: DateFormat = SimpleDateFormat("yyyy-MM-dd")
            val now: Date = formatter.parse("2023-02-10")
            val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
            val created: Date = dateFormat.parse(vodData?.getString("createdAt"))

            val timeDifference = now.time - created.time
            val daysDifference = timeDifference / (1000 * 3600 * 24)

            val broadcastType = vodData?.getString("broadcastType")?.toLowerCase()
            var startQuality = 8534030

            for ((resKey, resValue) in orderedResolutions) {
                var url: String? = null

                when {
                    broadcastType == "highlight" -> {
                        url = "https://$domain/$vodSpecialID/$resKey/highlight-${vodId}.m3u8"
                    }
                    broadcastType == "upload" && daysDifference > 7 -> {
                        url = "https://$domain/${channelData?.getString("login")}/$vodId/$vodSpecialID/$resKey/index-dvr.m3u8"
                    }
                    else -> {
                        url = "https://$domain/$vodSpecialID/$resKey/index-dvr.m3u8"
                    }
                }


                if (isValidQuality(url)) {
                    val quality = if (resKey == "chunked") "${resValue?.get("res").toString().split("x")[1]}p" else resKey
                    val enabled = if (resKey == "chunked") "YES" else "NO"
                    val fps = resValue?.get("fps")

                    fakePlaylist += """
#EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="$quality",NAME="$quality",AUTOSELECT=$enabled,DEFAULT=$enabled
#EXT-X-STREAM-INF:BANDWIDTH=$startQuality,CODECS="avc1.64002A,mp4a.40.2",RESOLUTION=${resValue?.get("res")},VIDEO="$quality",FRAME-RATE=$fps
$url"""

                    startQuality -= 100
                }
            }
            val header = Headers().apply {
                append("Content-Type", "application/vnd.apple.mpegurl")
            }




                    finalVideoUrl = Regex("https://[^\\s]+").find(fakePlaylist)?.value.toString()
                }
            }


            val playerAdapter = meta11ica.tn.twitchvod.BasicMediaPlayerAdapter(requireActivity())

            mTransportControlGlue = BasicTransportControlsGlue(requireContext(), playerAdapter)
            DetailsActivity.MOVIE
            //mTransportControlGlue.host = glueHost
            mTransportControlGlue.host = VideoSupportFragmentGlueHost(this@PlaybackVideoFragment)
            val movie = activity?.intent?.getSerializableExtra(DetailsActivity.MOVIE) as Movie
            movie.videoUrl = finalVideoUrl
            mTransportControlGlue.setPlaylist( listOf(movie))
            mTransportControlGlue.loadMovie(playlistPosition = 0)


            mTransportControlGlue.title = title
            mTransportControlGlue.subtitle = description
            playerAdapter.setDataSource(Uri.parse(finalVideoUrl))
            mTransportControlGlue.playWhenPrepared()


        }
    }

    override fun onPause() {
        super.onPause()
        mTransportControlGlue.pause()
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState) as ViewGroup
        //fastForwardIndicatorView = inflater.inflate(R.layout.view_playback_forward, view, false)
        //view.addView(fastForwardIndicatorView)
        //rewindIndicatorView = inflater.inflate(R.layout.view_playback_rewind, view, false)
        //view.addView(rewindIndicatorView)
        return view
    }




    suspend fun fetchTwitchDataGQL(vodId: String): JSONObject {
        val url = "https://gql.twitch.tv/gql"

        val headers = mapOf(
            "Client-ID" to "kimne78kx3ncx6brgo4mv6wki5h1ko", // Replace with your Twitch Client ID
            "Content-Type" to "application/json",
            "Accept" to "application/json"
        )
        var body = "{\"query\":\"query { video(id: \\\"$vodId\\\") { broadcastType, createdAt, seekPreviewsURL, owner { login } }}\"}"
        return JSONObject(post(url, headers = headers, data = body).text)
    }
    fun createServingID(): String {
        val w = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray()
        val random = java.util.Random()
        val id = StringBuilder()

        repeat(32) {
            id.append(w[random.nextInt(w.size)])
        }

        return id.toString()
    }
    suspend fun isValidQuality(url: String): Boolean {
        val connection = URL(url).openConnection()

        try {
            val response = connection.getInputStream()

            if (response != null) {
                response.bufferedReader().use { reader ->
                    val data = reader.readText()
                    return data.contains(".ts")
                }
            }
        } catch (e: Exception) {
            // Handle any exceptions if necessary
            e.printStackTrace()
        }

        return false
    }





}
class Headers {
    private val headers = mutableMapOf<String, String>()

    fun append(key: String, value: String) {
        headers[key] = value
    }

    // Add other header-related functions as needed

    fun getHeaders(): Map<String, String> {
        return headers.toMap()
    }



}