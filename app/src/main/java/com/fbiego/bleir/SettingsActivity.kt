package com.fbiego.bleir

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.fbiego.bleir.data.IRCode
import com.fbiego.bleir.data.ListAdapter
import kotlinx.android.synthetic.main.activity_settings.*
import timber.log.Timber
import java.io.*
import com.fbiego.bleir.MainActivity as MA

class SettingsActivity : AppCompatActivity() {

    private val fileNames = ArrayList<String>()
    private var listAdapter: ListAdapter? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val actionbar = supportActionBar
        actionbar!!.setDisplayHomeAsUpEnabled(true)

        val directory = this.filesDir
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val remote = File(directory, "remote")
        if (!remote.exists()) {
            remote.mkdirs()
        }
        val files = remote.listFiles()

        for( f in files!!){
            fileNames.add(f.nameWithoutExtension)
        }

        listAdapter = ListAdapter(this, fileNames, this@SettingsActivity::onClickDelete)
        remoteView.adapter = listAdapter

        //listAdapter?.notifyDataSetChanged()

    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun onClickDelete(name: String){

        val directory = this.filesDir
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val remote = File(directory, "remote")
        if (!remote.exists()) {
            remote.mkdirs()
        }

        val file = File(remote, "$name.txt")

        val dialog = AlertDialog.Builder(this)
        dialog.setTitle("Delete $name")
        dialog.setMessage("Are you sure?")
        dialog.setNegativeButton("No", null)
        dialog.setPositiveButton("Yes") { _, _ ->
            if (file.exists()){
                file.delete()
            }
            if (fileNames.contains(name)){
                fileNames.remove(name)
                listAdapter?.notifyDataSetChanged()
            }
        }
        dialog.show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.settings_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_add -> {

                if (checkExternal()) {
                    var chooseFile = Intent(Intent.ACTION_GET_CONTENT)
                    chooseFile.type = "*/*"
                    chooseFile = Intent.createChooser(chooseFile, "Choose a file")
                    startActivityForResult(chooseFile, MA.FILE_PICK)
                } else {
                    requestExternal()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            MA.STORAGE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    var chooseFile = Intent(Intent.ACTION_GET_CONTENT)
                    chooseFile.type = "text/plain"
                    chooseFile = Intent.createChooser(chooseFile, "Choose a file")
                    startActivityForResult(chooseFile, 20)
                } else {

                }
                return
            }
        }
    }
    private fun checkExternal(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                this@SettingsActivity,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return true
    }

    private fun requestExternal(){
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            MA.STORAGE
        )
    }

    @Throws(IOException::class)
    fun saveFile(src: File?, uri: Uri?) {

        if (src != null) {

            try {
                val `in`: InputStream = FileInputStream(src)
                val r = BufferedReader(InputStreamReader(`in`))
                val total = StringBuilder()
                var line: String?
                while (r.readLine().also { line = it } != null) {
                    total.append(line).append('\n')
                }
                val content = total.toString()

                parseData(content)
            } catch (e: Exception) {
            }

        }
        if (uri != null){

            try {
                val `in`: InputStream? = contentResolver.openInputStream(uri)
                val r = BufferedReader(InputStreamReader(`in`))
                val total = StringBuilder()
                var line: String?
                while (r.readLine().also { line = it } != null) {
                    total.append(line).append('\n')
                }
                val content = total.toString()

                parseData(content)
            } catch (e: Exception) {
            }


        }
    }

    private fun parseData(read: String){

        val directory = this.filesDir
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val remote = File(directory, "remote")
        if (!remote.exists()) {
            remote.mkdirs()
        }


        var data = ""
        val lines = read.split("\n")

        var no = 0
        for (l in lines){
            val dat = l.split(",")
            if (dat.size == 3){
                val name = dat[0]
                val protocol = dat[1].toIntOrNull()
                val code = dat[2].replace("0x", "", true).toLongOrNull(radix = 16)
                if (protocol != null && code != null){
                    data += "$name,$protocol,${code.toString(16)}\n"
                    //IRCode(name, protocol, code)
                    no++
                }
            }
        }


        val inflater = layoutInflater
        val dialogInflater = inflater.inflate(R.layout.input_name, null)
        val editText = dialogInflater.findViewById<EditText>(R.id.editName)

        val dialog = AlertDialog.Builder(this)
        dialog.setTitle("Read File")
        dialog.setMessage("Read $no lines")
        dialog.setNegativeButton("Cancel", null)
        if (no > 0){
            dialog.setView(dialogInflater)
            dialog.setPositiveButton("Save") { _, _ ->
                val name = editText.text.toString()
                if (name.isNotEmpty()) {

                    val dst = File(remote, "$name.txt")
                    dst.writeText(data)

                    if (!fileNames.contains(name)){
                        fileNames.add(name)
                        listAdapter?.notifyDataSetChanged()
                    }
                    Toast.makeText(this, "$name saved", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Not saved! Name not specified", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            dialog.setPositiveButton("Choose") { _, _ ->
                if (checkExternal()) {
                    var chooseFile = Intent(Intent.ACTION_GET_CONTENT)
                    chooseFile.type = "*/*"
                    chooseFile = Intent.createChooser(chooseFile, "Choose a file")
                    startActivityForResult(chooseFile, MA.FILE_PICK)
                } else {
                    requestExternal()
                }
            }
        }
        dialog.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Timber.e("RequestCode= $requestCode, ResultCode= $resultCode, Data= ${data != null}")
        if (resultCode == Activity.RESULT_OK) {
            if (data != null && requestCode == MA.FILE_PICK) {

                val selectedFile = data.data
                val filePathColumn = arrayOf(MediaStore.Files.FileColumns.DATA)
                if (selectedFile != null) {
                    val cursor =
                        contentResolver.query(selectedFile, filePathColumn, null, null, null)
                    if (cursor != null) {
                        cursor.moveToFirst()
                        val columnIndex = cursor.getColumnIndex(filePathColumn[0])

                        //Timber.e(filePathColumn.contentDeepToString())
                        //Timber.e("index = $columnIndex")
                        val filePath = cursor.getString(columnIndex)

                        //val f = File(filePath)
                        cursor.close()

                        //saveFile(f)
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q){
                            saveFile(File(filePath), null)
                        } else {
                            saveFile(null, selectedFile)
                        }
                    }
                }
            }
        }
    }
}