package com.example.visitingcardscanner

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
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
    var notes: String = ""
)

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else toast("Camera permission is required.")
    }

    private val galleryPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { processImage(InputImage.fromFilePath(this, it)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cameraButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) startCamera()
            else cameraPermission.launch(Manifest.permission.CAMERA)
        }

        binding.galleryButton.setOnClickListener { galleryPicker.launch("image/*") }

        binding.saveButton.setOnClickListener { saveToContacts() }

        binding.rescanButton.setOnClickListener { showHome() }
    }

    private fun startCamera() {
        binding.homeButtons.visibility = View.GONE
        binding.resultScroll.visibility = View.GONE
        binding.previewView.visibility = View.VISIBLE

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }
            imageCapture = ImageCapture.Builder().build()
            provider.unbindAll()
            provider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
            )

            binding.previewView.setOnClickListener { takePhoto() }
            binding.subtitle.text = "ছবি তুলতে স্ক্রিনে ট্যাপ করুন"
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val file = File(cacheDir, "card_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()

        capture.takePicture(options, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    toast("ছবি তোলা যায়নি")
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    binding.previewView.visibility = View.GONE
                    processImage(InputImage.fromFilePath(this@MainActivity, file.toUri()))
                }
            })
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
                binding.notesInput.setText(data.notes)
                binding.status.text = "তথ্য পাওয়া গেছে। প্রয়োজন হলে ঠিক করে Save to Contacts চাপুন।"
            }
            .addOnFailureListener {
                binding.status.text = "কার্ডের লেখা পড়তে সমস্যা হয়েছে। পরিষ্কার ছবি দিয়ে আবার চেষ্টা করুন।"
            }
    }

    private fun saveToContacts() {
        val name = binding.nameInput.text?.toString()?.trim().orEmpty()
        val phone = binding.phoneInput.text?.toString()?.trim().orEmpty()
        val email = binding.emailInput.text?.toString()?.trim().orEmpty()

        if (name.isBlank() && phone.isBlank() && email.isBlank()) {
            toast("নাম, ফোন বা ইমেইল অন্তত একটি তথ্য দিন")
            return
        }

        val canRead = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (canRead && hasPossibleDuplicate(phone, email)) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("সম্ভাব্য Duplicate Contact")
                .setMessage("এই ফোন বা ইমেইল দিয়ে আগে থেকেই একটি contact থাকতে পারে। তবুও নতুন contact তৈরি করবেন?")
                .setNegativeButton("বাতিল", null)
                .setPositiveButton("তৈরি করুন") { _, _ -> launchContactEditor() }
                .show()
        } else {
            launchContactEditor()
        }
    }

    private fun hasPossibleDuplicate(phone: String, email: String): Boolean {
        val phoneDigits = phone.filter(Char::isDigit)
        val emailNorm = email.trim().lowercase()

        if (phoneDigits.isBlank() && emailNorm.isBlank()) return false

        val cursor = contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.DATA1, ContactsContract.Data.MIMETYPE),
            null, null, null
        ) ?: return false

        cursor.use {
            while (it.moveToNext()) {
                val value = it.getString(0).orEmpty()
                val digits = value.filter(Char::isDigit)
                if (phoneDigits.length >= 8 && digits.endsWith(phoneDigits.takeLast(8))) return true
                if (emailNorm.isNotBlank() && value.trim().equals(emailNorm, ignoreCase = true)) return true
            }
        }
        return false
    }

    private fun launchContactEditor() {
        val name = binding.nameInput.text?.toString()?.trim().orEmpty()
        val phone = binding.phoneInput.text?.toString()?.trim().orEmpty()
        val email = binding.emailInput.text?.toString()?.trim().orEmpty()
        val company = binding.companyInput.text?.toString()?.trim().orEmpty()
        val job = binding.jobInput.text?.toString()?.trim().orEmpty()
        val notes = binding.notesInput.text?.toString()?.trim().orEmpty()

        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, name)
            putExtra(ContactsContract.Intents.Insert.PHONE, phone)
            putExtra(ContactsContract.Intents.Insert.EMAIL, email)
            putExtra(ContactsContract.Intents.Insert.COMPANY, company)
            putExtra(ContactsContract.Intents.Insert.JOB_TITLE, job)
            putExtra(ContactsContract.Intents.Insert.NOTES, notes)
        }
        startActivity(intent)
    }

    private fun showHome() {
        binding.resultScroll.visibility = View.GONE
        binding.previewView.visibility = View.GONE
        binding.homeButtons.visibility = View.VISIBLE
        binding.subtitle.text = "কার্ডের ছবি তুলুন বা গ্যালারি থেকে নিন"
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        recognizer.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
