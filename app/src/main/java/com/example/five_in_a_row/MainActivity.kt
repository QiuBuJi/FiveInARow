package com.example.five_in_a_row

import android.content.Context
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    companion object {
        lateinit var tvTitle: TextView
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTitle = main_tvTitle

        val adapter = Adapter(this, main_rvTable)
        main_rvTable.adapter = adapter
        main_rvTable.layoutManager = GridLayoutManager(this, adapter.width)
    }

    class Adapter(
        private val context: Context,
        val rvTable: RecyclerView,
        val width: Int = 10,
        val height: Int = 16
    ) : RecyclerView.Adapter<Holder>() {
        private val table = ArrayList<Circle>()

        init {
            val size = width * height
            for (index in 1..size) table.add(Circle())
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val from = LayoutInflater.from(context)
                .inflate(R.layout.sample_item_circle, parent, false)
            return Holder(from)
        }

        override fun getItemCount(): Int = table.size

        private val CircleType.resource: Int
            get() {
                return when (this) {
                    CircleType.Black -> R.drawable.ic_bg_circle_black
                    CircleType.White -> R.drawable.ic_bg_circle_white
                    else -> R.drawable.ic_bg_rectangle
                }
            }

        private val CircleType.alter: CircleType
            get() {
                return when (this) {
                    CircleType.White -> CircleType.Black
                    else -> CircleType.White
                }
            }

        private var oldType: CircleType = CircleType.White
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val circle = table[position]
            holder.run {
                tvCircle.text = if (circle.showText) circle.text else ""
                val size = rvTable.width / width
                holder.itemView.layoutParams.width = size
                holder.itemView.layoutParams.height = size
                tvTitle.text = "${oldType.alter.name1}下棋："


                tvCircle.setBackgroundResource(circle.type.resource)
                tvCircle.setOnClickListener {
                    if (circle.show) return@setOnClickListener

                    oldType = oldType.alter
                    circle.show = true
                    circle.type = oldType
                    notifyItemChanged(position)
                }
            }
        }

    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCircle: TextView = itemView.findViewById(R.id.circle_tvTxt)
    }
}

data class Circle(
    var type: CircleType = CircleType.None,
    var show: Boolean = false,
    var showText: Boolean = false,
    var text: String = ""
)

enum class CircleType {
    None, White, Black;

    val name1: String
        get() {
            return when (this) {
                White -> "白棋"
                Black -> "黑棋"
                else -> "空"
            }
        }
}