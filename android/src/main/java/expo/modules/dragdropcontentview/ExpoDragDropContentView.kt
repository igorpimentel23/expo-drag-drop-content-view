package expo.modules.dragdropcontentview

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.view.DragStartHelper
import androidx.draganddrop.DropHelper
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView
import android.view.View
import android.view.ViewGroup
import java.io.File

@SuppressLint("ViewConstructor")
class ExpoDragDropContentView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {
    private var includeBase64 = false
    private var draggableImageUris: List<String> = emptyList()
    private var highlightColor = ContextCompat.getColor(context, R.color.highlight_color)
    private var highlightBorderRadius = 0
    private val onDropEvent by EventDispatcher()

    private val utils = Utils()

    fun setIncludeBase64(value: Boolean?) {
        includeBase64 = value ?: false
    }

    fun setDraggableImageUris(value: List<String>?) {
        draggableImageUris = value ?: emptyList()
    }

    fun setHighlightBorderRadius(value: Int?) {
        if (value != null) {
            highlightBorderRadius = value
            configureDropHelper(this)
        }
    }

    fun setHighlightColor(value: Int?) {
        if (value != null) {
            highlightColor = value
            configureDropHelper(this)
        }
    }

    private fun configureDropHelper(frame: View) {
        // DropHelper is only available on Android N and above
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;

        val activity = appContext.activityProvider?.currentActivity!!
        val contentResolver = context.contentResolver

        DropHelper.configureView(
            activity,
            frame,
            arrayOf("image/*"),
            DropHelper.Options.Builder()
                .setHighlightColor(highlightColor)
                .setHighlightCornerRadiusPx(highlightBorderRadius)
                .build()
        ) { _, payload ->
            val clipData = payload.clip
            val infoList = mutableListOf<Map<String, Any?>>()

            for (i in 0 until clipData.itemCount) {
                val contentUri = clipData.getItemAt(i).uri
                if (contentUri != null) {
                    val info = utils.getFileInfo(contentResolver, contentUri, includeBase64, frame.context)
                    info?.let { infoList.add(it) }
                }
            }
            if (infoList.isNotEmpty()) onDropEvent(mapOf("assets" to infoList))
            return@configureView null
        }
    }

    private fun configureDragHelper(frame: View) {
        // DropHelper is only available on Android N and above
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;

        val contentResolver = context.contentResolver

        DragStartHelper(frame) { view, _ ->
            val data: MutableList<Uri> = mutableListOf()

            for (imageUri in draggableImageUris) {
                val path = Uri.parse(imageUri).path

                if (!path.isNullOrBlank()) {
                    val file = File(path)
                    val uri = utils.getContentUriForFile(frame.context, file)
                    uri?.let { data.add(it) }
                }
            }

            val shadow = DragShadowBuilder(view)

            if (data.isNotEmpty()) {
                val clipData = ClipData.newUri(contentResolver, "Image", data[0])

                for (i in 1 until data.size) {
                    clipData.addItem(ClipData.Item(data[i]))
                }

                view.startDragAndDrop(clipData, shadow, null, DRAG_FLAG_GLOBAL or DRAG_FLAG_GLOBAL_URI_READ)
            }
            else {
                return@DragStartHelper false
            }
        }.attach()
    }

    private fun addDragToChild(view: View) {
        configureDragHelper(view)

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                addDragToChild(view.getChildAt(i))
            }
        }
    }

    override fun addView(child: View?, index: Int) {
        super.addView(child, index)
        if (child != null) addDragToChild(child)
    }

    init {
        configureDropHelper(this)
        configureDragHelper(this)
    }
}
