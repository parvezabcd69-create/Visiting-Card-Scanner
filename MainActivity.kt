package com.example.visitingcardscanner

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.visitingcardscanner.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.Executors

data class CardData(
    var name: String = "",
    var phone: String = "",
    var email: String = "",
    var company: String = "",
    var jobTitle: String = "",
    var website: String = "",
    var address: String = "",
    var notes: String = ""
)

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else toast("ক্যামেরার অনুমতি প্রয়োজন।")
    }

    private val galleryPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                processImage(InputImage.fromFilePath(this, it))
            } catch (_: Exception) {
                toast("ছবিটি খোলা যায়নি। অন্য ছবি চেষ্টা করুন।")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cameraButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startCamera()
            } else {
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
        }

        binding.galleryButton.setOnClickListener {
            galleryPicker.launch("image/*")
        }

        binding.saveButton.setOnClickListener {
            saveToContacts()
        }

        binding.rescanButton.setOnClickListener {
            showHome()
        }

        binding.aiButton.setOnClickListener {
            binding.status.text =
                "অনলাইন AI এখনো সংযুক্ত হয়নি। আপাতত অফলাইন OCR-এর তথ্য যাচাই করে সম্পাদনা করুন।"
            toast("AI সার্ভার পরের ধাপে সংযুক্ত করা হবে।")
        }
    }

    private fun startCamera() {
        binding.homeButtons.visibility = View.GONE
        binding.resultScroll.visibility = View.GONE
        binding.previewView.visibility = View.VISIBLE

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = binding.previewView.surfaceProvider
                }

                imageCapture = ImageCapture.Builder().build()
                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )

                binding.previewView.setOnClickListener { takePhoto() }
                binding.subtitle.text = "ছবি তুলতে স্ক্রিনে ট্যাপ করুন"
            } catch (_: Exception) {
                toast("ক্যামেরা চালু করা যায়নি।")
                showHome()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val file = File(cacheDir, "card_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()

        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    toast("ছবি তোলা যায়নি।")
                }

                override fun onImageSaved(
                    output: ImageCapture.OutputFileResults
                ) {
                    try {
                        binding.previewView.visibility = View.GONE
                        processImage(
                            InputImage.fromFilePath(
                                this@MainActivity, file.toUri()
                            )
                        )
                    } catch (_: Exception) {
                        toast("ছবিটি পড়া যায়নি। আবার চেষ্টা করুন।")
                    }
                }
            }
        )
    }

    private fun processImage(image: InputImage) {
        binding.homeButtons.visibility = View.GONE
        binding.previewView.visibility = View.GONE
        binding.resultScroll.visibility = View.VISIBLE
        binding.status.text = "কার্ডের লেখা পড়া হচ্ছে…"

        recognizer.process(image)
            .addOnSuccessListener { result ->
                val data = CardParser.parse(result.text)

                binding.nameInput.setText(data.name)
                binding.phoneInput.setText(data.phone)
                binding.emailInput.setText(data.email)
                binding.companyInput.setText(data.company)
                binding.jobInput.setText(data.jobTitle)
                binding.websiteInput.setText(data.website)
                binding.addressInput.setText(data.address)
                binding.notesInput.setText(data.notes)

                binding.status.text =
                    "অফলাইন স্ক্যান সম্পন্ন। তথ্যগুলো যাচাই করে নিন।"
            }
            .addOnFailureListener {
                binding.status.text =
                    "লেখা পড়া যায়নি। আরও পরিষ্কার ছবি দিয়ে চেষ্টা করুন।"
            }
    }

    private fun saveToContacts() {
        val name = binding.nameInput.text?.toString()?.trim().orEmpty()
        val phone = binding.phoneInput.text?.toString()?.trim().orEmpty()
        val email = binding.emailInput.text?.toString()?.trim().orEmpty()

        if (name.isBlank() && phone.isBlank() && email.isBlank()) {
            toast("নাম, ফোন বা ইমেইল অন্তত একটি দিন।")
            return
        }

        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE

            putExtra(ContactsContract.Intents.Insert.NAME, name)
            putExtra(ContactsContract.Intents.Insert.PHONE, phone)
            putExtra(ContactsContract.Intents.Insert.EMAIL, email)
            putExtra(
                ContactsContract.Intents.Insert.COMPANY,
                binding.companyInput.text?.toString()?.trim().orEmpty()
            )
            putExtra(
                ContactsContract.Intents.Insert.JOB_TITLE,
                binding.jobInput.text?.toString()?.trim().orEmpty()
            )
            putExtra(
                ContactsContract.Intents.Insert.POSTAL,
                binding.addressInput.text?.toString()?.trim().orEmpty()
            )
            putExtra(
                ContactsContract.Intents.Insert.NOTES,
                binding.notesInput.text?.toString()?.trim().orEmpty()
            )

            val website = binding.websiteInput.text
                ?.toString()?.trim().orEmpty()

            if (website.isNotBlank()) {
                val websiteValues = ContentValues().apply {
                    put(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE
                    )
                    put(
                        ContactsContract.CommonDataKinds.Website.URL,
                        website
                    )
                    put(
                        ContactsContract.CommonDataKinds.Website.TYPE,
                        ContactsContract.CommonDataKinds.Website.TYPE_WORK
                    )
                }

                putParcelableArrayListExtra(
                    ContactsContract.Intents.Insert.DATA,
                    arrayListOf(websiteValues)
                )
            }
        }

        try {
            startActivity(intent)
        } catch (_: Exception) {
            toast("Contacts অ্যাপ খোলা যায়নি।")
        }
    }

    private fun showHome() {
        binding.resultScroll.visibility = View.GONE
        binding.previewView.visibility = View.GONE
        binding.homeButtons.visibility = View.VISIBLE
        binding.subtitle.text = "কার্ডের ছবি তুলুন বা গ্যালারি থেকে নিন"
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        recognizer.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}