package com.example.five_in_a_row

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        title = "关于"

        //添加返回按钮
        supportActionBar?.run {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        item?.run {
            //返回
            if (itemId == android.R.id.home) finish()
        }
        return super.onOptionsItemSelected(item)
    }
}
