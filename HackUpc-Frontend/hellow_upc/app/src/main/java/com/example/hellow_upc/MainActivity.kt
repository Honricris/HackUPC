package com.example.hellow_upc
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.app.Activity
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.Toast
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import java.io.File
import java.io.FileOutputStream
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat

val url = "https://5071-147-83-201-134.ngrok-free.app" // Actualiza con la URL correcta de tu API
private var imageBitmap: Bitmap? = null
private var flightNumber: String = ""
private var flightDetails: FlightDetails? = null


class MainActivity : AppCompatActivity() {

    private lateinit var takePictureLauncher: ActivityResultLauncher<Intent>
    private lateinit var selectImageLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val takeImageBtn = findViewById<Button>(R.id.image_btn)
        val selectImageBtn = findViewById<Button>(R.id.select_image_btn)
        val flightInfo = findViewById<Button>(R.id.speak)
        val returnBtn = findViewById<Button>(R.id.returnBtn)
        returnBtn.visibility= View.GONE
        flightInfo.isEnabled=false

        // Listeners
        takeImageBtn.setOnClickListener {
            dispatchTakePictureIntent()
            returnBtn.visibility= View.VISIBLE
            takeImageBtn.visibility= View.GONE
            selectImageBtn.visibility= View.GONE
            flightInfo.isEnabled = true
            getFlightData(imageBitmap ?: return@setOnClickListener)

        }

        selectImageBtn.setOnClickListener {
            dispatchSelectImageIntent()
            returnBtn.visibility= View.VISIBLE
            takeImageBtn.visibility= View.GONE
            selectImageBtn.visibility= View.GONE
            flightInfo.isEnabled = true
            getFlightData(imageBitmap ?: return@setOnClickListener)


        }
        flightInfo.setOnClickListener {
            getFlightInfoSpeech(flightNumber)
        }


        returnBtn.setOnClickListener{
            // Volvemos a activar los dos botones y ocultamos el botón de retorno
            flightInfo.isEnabled = true
            selectImageBtn.visibility = View.VISIBLE
            takeImageBtn.visibility = View.VISIBLE
            returnBtn.visibility = View.GONE
        }

        // Inicialización del lanzador para capturar la imagen
        takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                if (result.data != null) {
                    val selectedImageUri: Uri? = result.data?.data
                    if (selectedImageUri != null) {
                        // Obtener bitmap de la imagen seleccionada
                        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, selectedImageUri)
                        // Subir la imagen
                        uploadImage(bitmap)
                    } else {
                        showToast("Error: No se pudo obtener la imagen seleccionada")
                    }
                }
            }
        }

        // Inicialización del lanzador para seleccionar la imagen de la galería
        selectImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                if (result.data != null) {
                    val selectedImageUri: Uri? = result.data?.data
                    if (selectedImageUri != null) {
                        // Obtener bitmap de la imagen seleccionada
                        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, selectedImageUri)
                        // Subir la imagen
                        uploadImage(bitmap)
                    } else {
                        showToast("Error: No se pudo obtener la imagen seleccionada")
                    }
                }
            }
        }

    }
    private fun getFlightInfoSpeech(flightNumber: String) {
        val retrofit = Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(FileUploadService::class.java)
        val call = service.getFlightInfoSpeech(flightNumber)

        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                // Manejar la respuesta del servidor aquí
                if(response.isSuccessful){
                    // La respuesta es exitosa
                    val responseBody = response.body()
                    if (responseBody != null) {
                        // Guardar el archivo mp3 localmente
                        val inputStream = responseBody.byteStream()
                        val file = File(cacheDir, "audio.mp3")
                        val outputStream = FileOutputStream(file)
                        val buffer = ByteArray(1024)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                        outputStream.close()
                        inputStream.close()

                        // Reproducir el archivo mp3 localmente
                        val mediaPlayer = MediaPlayer.create(applicationContext, Uri.fromFile(file))
                        mediaPlayer.setOnCompletionListener {
                            getFlightDataJson()
                        }
                        mediaPlayer.start()

                    } else {
                        showToast("Error: La respuesta no es correcta")
                    }
                } else {
                    showToast("Error: Respuesta nula del servidor")
                    val responseBody = response.errorBody()?.string()
                    if (responseBody != null) {
                        Log.d("Response Content", responseBody)
                    }
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                // Manejar errores aquí
                Log.e("API Call", "Error al realizar la llamada API", t)
            }
        })
    }

    private fun getFlightDataJson() {
        val retrofit = Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(FileUploadService::class.java)
        val call = service.getFlightDetails(flightNumber)

        call.enqueue(object : Callback<FlightDetails> {
            override fun onResponse(call: Call<FlightDetails>, response: Response<FlightDetails>) {
                if (response.isSuccessful) {
                    val flightDetailsLocal = response.body()
                    if (flightDetailsLocal != null) {
                        // Aquí puedes manejar los datos del vuelo obtenidos
                        showToast("Detalles del vuelo: $flightDetailsLocal")
                        if (flightDetailsLocal != flightDetails) {
                            // Registro para verificar si se cumple la condición para mostrar la notificación
                            Log.d("NotificationCheck", "Los detalles del vuelo han sido modificados")
                            // notificacion push de que se han actualizado los datos
                            showNotification(1)
                        } else {
                            // Registro para verificar si se cumple la condición para mostrar la notificación
                            Log.d("NotificationCheck", "No hay modificaciones en los detalles del vuelo")
                            // notificacion push de que no hay modificaciones en los datos
                            showNotification(0)
                        }
                    } else {
                        showToast("Error: No se pudieron obtener los detalles del vuelo")
                    }
                } else {
                    showToast("Error: No se pudo obtener una respuesta exitosa del servidor")
                }
            }

            override fun onFailure(call: Call<FlightDetails>, t: Throwable) {
                showToast("Error: Falló la llamada a la API")
            }
        })
    }

    private fun showNotification(num: Number) {
        // Crear un intent para la actividad que se abrirá al hacer clic en la notificación
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        // Crear el canal de notificación (necesario para Android 8.0 y versiones posteriores)
        val channelId = "my_channel_id"
        val channelName = "My Channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "My Channel Description"
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        var notificationBuilder: NotificationCompat.Builder
        // Construir la notificación
        if(num==0){
            notificationBuilder = NotificationCompat.Builder(this, channelId)
                .setContentTitle("El vuelo ha sido actualizado")
                .setContentText("revisa la información del vuelo. Ha sido actualizado")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(android.R.drawable.ic_dialog_info) // Icono de información predeterminado
                .setContentIntent(pendingIntent)
                .setAutoCancel(true) // Hacer que la notificación se cierre al hacer clic en ella
        }else{
            notificationBuilder = NotificationCompat.Builder(this, channelId)
                .setContentTitle("El vuelo no ha sufrido cambios")
                .setContentText("no te preocupes jeje")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(android.R.drawable.ic_dialog_info) // Icono de información predeterminado
                .setContentIntent(pendingIntent)
                .setAutoCancel(true) // Hacer que la notificación se cierre al hacer clic en ella
        }

        // Mostrar la notificación
        notificationManager.notify(1, notificationBuilder.build())
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        takePictureLauncher.launch(takePictureIntent)
    }

    private fun dispatchSelectImageIntent() {
        val selectImageIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        selectImageLauncher.launch(selectImageIntent)
    }

    private fun uploadImage(bitmap: Bitmap) {
        val file = bitmapToFile(bitmap)
        val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("image", file.name, requestFile)

        val retrofit = Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(FileUploadService::class.java)
        val call = service.uploadFile(body)

        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                // Manejar la respuesta del servidor aquí
                if(response.isSuccessful){
                    // La respuesta es exitosa
                    val responseBody = response.body()
                    if (responseBody != null) {
                        // Guardar el archivo mp3 localmente
                        val inputStream = responseBody.byteStream()
                        val file = File(cacheDir, "audio.mp3")
                        val outputStream = FileOutputStream(file)
                        val buffer = ByteArray(1024)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                        outputStream.close()
                        inputStream.close()

                        // Reproducir el archivo mp3 localmente
                        val mediaPlayer = MediaPlayer.create(applicationContext, Uri.fromFile(file))
                        mediaPlayer.setOnCompletionListener(MediaPlayer.OnCompletionListener {
                            // Una vez que el audio se haya reproducido completamente, se ejecuta la siguiente función
                            getFlightData(bitmap)
                        })
                        mediaPlayer.start()

                    } else {
                        showToast("Error: La respuesta no es correcta")
                    }
                } else {
                    showToast("Error: Respuesta nula del servidor")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                // Manejar errores aquí
            }
        })
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun bitmapToFile(bitmap: Bitmap): File {
        val file = File(cacheDir, "image.jpg")
        file.createNewFile()
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
        return file
    }

    private fun getFlightData(bitmap: Bitmap) {

        val file = bitmapToFile(bitmap)
        val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("image", file.name, requestFile)

        val retrofit = Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(FileUploadService::class.java)
        val call = service.extractFlightNumber(body)
        call.enqueue(object : Callback<FlightData> {
            override fun onResponse(call: Call<FlightData>, response: Response<FlightData>) {
                if (response.isSuccessful) {
                    val flightData = response.body()
                    flightNumber = flightData?.flightNumber ?: "No se encontró número de vuelo"
                    showToast("Número de vuelo: $flightNumber")
                } else {
                    showToast("Error al obtener el número de vuelo")
                }
            }

            override fun onFailure(call: Call<FlightData>, t: Throwable) {
                showToast("Error al comunicarse con el servidor")
            }
        })
    }
}

interface FileUploadService {
    @Multipart
    @POST("upload")
    fun uploadFile(@Part filePart: MultipartBody.Part): Call<ResponseBody>

    @Multipart
    @POST("extract-flight-number")
    fun extractFlightNumber(@Part filePart: MultipartBody.Part): Call<FlightData>

    @GET("flight-info-speech/{flightNumber}")
    fun getFlightInfoSpeech(@Path("flightNumber") flightNumber: String): Call<ResponseBody>
    @GET("flight-details/{flightNumber}")
    fun getFlightDetails(@Path("flightNumber") flightNumber: String): Call<FlightDetails>
}

data class FlightDetails(
    val flightNumber: String,
    val flightStatus: String,
    val departureTime: String,
    val departureGate: String,
)

data class FlightData(val flightNumber: String)
