package com.example.digital_ink_recognition_and_ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.bottomappbar.BottomAppBar
import com.example.digital_ink_recognition_and_ocr.databinding.ActivityMainBinding
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.digitalink.Ink
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.RecognitionResult
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.jsoup.nodes.*
import org.jsoup.select.Elements
import org.jsoup.HttpStatusException

/*
    https://developers.google.com/ml-kit/vision/digital-ink-recognition/android
    https://developers.googleblog.com/2020/08/digital-ink-recognition-in-ml-kit.html

    TODO:
    - get definition of character match?
    - handle multiple pinyin for a character?
    - add OCR feature (camera + upload pic)?
 */


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var snackbarOn : Boolean = true
    private var fetchPinyinOn: Boolean = true
    private var copyCharToClipboard: Boolean = true

    // https://stackoverflow.com/questions/43680655/snackbar-sometimes-doesnt-show-up-when-it-replaces-another-one
    private var currSnackbar : Snackbar? = null

    private fun showSnackbar(msgId: Int){
        if(snackbarOn) {
            val snackbar = Snackbar.make(binding.container, msgId, Snackbar.LENGTH_INDEFINITE)
            snackbar.show()
            currSnackbar = snackbar
        }
    }

    private fun doInkRecognition(model: DigitalInkRecognitionModel, inkData: Ink){
        showSnackbar(R.string.processing_msg)

        val recognizer: DigitalInkRecognizer = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(model).build()
        )

        recognizer.recognize(inkData)
            .addOnSuccessListener { result: RecognitionResult ->
                // TODO: also get the definition of the character(s)
                // also handle possibility of multiple characters?
                val res = result.candidates[0].text

                Log.i("INFO", res)

                // https://stackoverflow.com/questions/53582860/is-networkonmainthreadexception-valid-for-a-network-call-in-a-coroutine
                lifecycleScope.launch {
                    // what if multiple definitions/pinyin?
                    // try a different source like chinese-tools?
                    if(fetchPinyinOn) {
                        var pinyin = ""
                        try {
                            val doc = withContext(Dispatchers.IO) {
                                Jsoup.connect("https://en.wiktionary.org/wiki/$res")
                                    .get() as Document
                            }
                            Log.d("DEBUG", doc.title())

                            val pinyinResults = doc.select(".form-of") as Elements

                            if(pinyinResults.count() > 0) {
                                pinyin = pinyinResults[0].text()
                            }
                        } catch (err: HttpStatusException) {
                        }
                        showPopup("best match: $res\npinyin: $pinyin")
                    }else{
                        showPopup("best match: $res")
                    }

                    currSnackbar?.dismiss()
                }

                // add the matching character to clipboard for pasting somewhere if needed
                // https://stackoverflow.com/questions/19253786/how-to-copy-text-to-clip-board-in-android
                if(copyCharToClipboard) {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("", res) as ClipData
                    clipboard.setPrimaryClip(clip)
                }
            }
            .addOnFailureListener { e: Exception ->
                showSnackbar(R.string.failure_msg_recognition)
                Log.e("ERROR", "Error during recognition: $e")
            }
    }

    private suspend fun getAndProcessInkData(){
        showSnackbar(R.string.starting_msg)

        withContext(Dispatchers.Default) {
            // get the ink data from CanvasView
            val inkData: Ink = binding.canvasView.getInkData()

            var modelIdentifier: DigitalInkRecognitionModelIdentifier? = null
            try {
                // traditional chinese
                modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("zh-TW")
            } catch (e: MlKitException) {
                // language tag failed to parse, handle error.
            }

            if (modelIdentifier != null) {
                var model: DigitalInkRecognitionModel =
                    DigitalInkRecognitionModel.builder(modelIdentifier).build()
                val remoteModelManager = RemoteModelManager.getInstance()

                showSnackbar(R.string.check_download_language_msg)

                remoteModelManager.isModelDownloaded(model).addOnSuccessListener { bool ->
                    when (bool) {
                        true -> {
                            showSnackbar(R.string.downloaded_language_msg)
                            doInkRecognition(model, inkData)
                        }
                        false -> {
                            // download it
                            showSnackbar(R.string.download_language_msg)
                            Log.i("INFO", "NEED TO DOWNLOAD MODEL")

                            remoteModelManager.download(model, DownloadConditions.Builder().build())
                                .addOnSuccessListener {
                                    Log.i("INFO", "Model downloaded")
                                    showSnackbar(R.string.downloaded_language_msg)
                                    doInkRecognition(model, inkData)
                                }
                                .addOnFailureListener { e: Exception ->
                                    showSnackbar(R.string.failure_msg_download)
                                    Log.e("ERROR", "Error while downloading a model: $e")
                                }
                        }
                    }
                }
            }else{
                // no model was found, handle error.
            }
        }
    }

    private fun showPopup(text: String?){
        // https://stackoverflow.com/questions/5944987/how-to-create-a-popup-window-popupwindow-in-android
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.popup_window, null)
        val popupWindow = PopupWindow(
            popupView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true // focusable - allows closing of the popup when tapping outside of it
        )

        val popupText = popupView.findViewById(R.id.popup_text) as TextView
        if(text != null) popupText.text = text

        popupText.setTextColor(Color.BLACK)

        popupWindow.showAtLocation(binding.root, Gravity.TOP, 0, 0)

        popupView.setOnTouchListener(View.OnTouchListener { view, event ->
            popupWindow.dismiss()
            true
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // https://stackoverflow.com/questions/42688815/add-click-event-on-menu-item
        // https://stackoverflow.com/questions/44611224/how-to-setonnavigationitemlistener-on-bottomnavigationview-in-android-using-kotl
        // https://stackoverflow.com/questions/64036179/bottomappbar-and-handle-onclicknavigation
        val navView: BottomAppBar = binding.navView

        navView.setNavigationOnClickListener {
            // TODO: handle navigation icon press
            // switch between OCR and digital-ink recognition here?
        }

        val fab = binding.container.findViewById(R.id.analyze_drawing) as FloatingActionButton
        fab.setOnClickListener { _ ->
            // https://stackoverflow.com/questions/49433721/how-to-showpopupwindow-after-asynctask-finished
            // https://developer.android.com/kotlin/coroutines#use-coroutines-for-main-safety
            // https://developer.android.com/kotlin/coroutines/coroutines-adv#main-safety
            // https://kotlinlang.org/docs/coroutines-basics.html#your-first-coroutine
            // https://stackoverflow.com/questions/63204706/how-to-use-coroutine-inside-a-floatingactionbutton-button-click-event
            lifecycleScope.launch { getAndProcessInkData() }
        }

        navView.setOnMenuItemClickListener { item ->
            when(item.itemId){
                R.id.about -> {
                    showPopup("This is a mobile app to help make it easier to get the pinyin (and maybe definition) of Chinese characters just by writing them out. :)\n\n" +
                            "Write out a Chinese character on the screen and then click the middle button at the bottom to get back the best match.\n\n" +
                            "Also note that by default, the pinyin of the best matching character will be fetched, which requires internet access. You can turn it off in the overflow menu control button on the bottom right.")
                    true
                }
                R.id.toggle_snackbar -> {
                    if(item.title == "turn snackbar off"){
                        item.title = "turn snackbar on"
                    }else{
                        item.title = "turn snackbar off"
                    }
                    snackbarOn = !snackbarOn
                    true
                }
                R.id.toggle_pinyin_definition_fetch -> {
                    if(item.title == "get pinyin: on"){
                        item.title = "get pinyin: off"
                    }else{
                        item.title = "get pinyin: on"
                    }
                    fetchPinyinOn = !fetchPinyinOn
                    true
                }
                R.id.copy_char_to_clipboard -> {
                    if(item.title == "copy to clipboard: on"){
                        item.title = "copy to clipboard: off"
                    }else{
                        item.title = "copy to clipboard: on"
                    }
                    copyCharToClipboard = !copyCharToClipboard
                    true
                }
                R.id.clear_canvas -> {
                    // clear the canvas and reset inkbuilder
                    binding.canvasView.clear()
                    true
                }
                else -> false
            }
        }

    }
}