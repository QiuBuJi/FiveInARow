package com.example.five_in_a_row

import android.content.Context
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Message
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

        main_btClear.setOnClickListener {
            main_rvTable.swapAdapter(Adapter(this, main_rvTable), true)
        }
    }

    //*************************************** Adapter **********************************************
    class Adapter(
        private val context: Context,
        private val rvTable: RecyclerView,
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

        //******************************************************************************************
        private val Circle.TYPE.resource: Int
            get() {
                return when (this) {
                    Circle.TYPE.Black -> R.drawable.ic_bg_circle_black
                    Circle.TYPE.White -> R.drawable.ic_bg_circle_white
                    Circle.TYPE.Test -> R.drawable.ic_bg_circle_blue
                    else -> R.drawable.ic_bg_rectangle
                }
            }

        /**通知数据改变了*/
        private fun Circle.notifyChange() {
            val position = table.indexOf(this)
            if (position >= 0) notifyItemChanged(position)
        }

        /**通知数据改变了*/
        private fun CircleD.notifyChange() = circle.notifyChange()

        /**遍历包围该位置的所有数据*/
        private fun Circle.traverseSurrounds(action: (circleD: CircleD) -> Boolean) {
            val position = table.indexOf(this)
            if (position < 0) return

            for (times in -1..1) {
                val y = position + (times * width)
                val cx = y / width
                if (y < 0) continue//上越界

                for (x in -1..1) {
                    val index = y + x
                    val cxTemp = index / width

                    //规避越界错误
                    if ((cx != cxTemp) ||//左右越界
                        (times == 0 && x == 0) ||//中间
                        (index < 0) || (index >= table.size)//上下越界
                    ) continue

                    val circleD = CircleD(table[index], getDirection(x, times))
                    if (!action(circleD)) return
                }
            }
        }

        /**转换为方位数据*/
        private fun getDirection(x: Int, y: Int): Direction {
            return when (x) {
                -1 ->
                    when (y) {
                        -1 -> Direction.LeftTop
                        0 -> Direction.Left
                        else -> Direction.LeftBottom
                    }
                0 ->
                    when (y) {
                        -1 -> Direction.Top
                        0 -> Direction.None
                        else -> Direction.Bottom
                    }
                else ->
                    when (y) {
                        -1 -> Direction.RightTop
                        0 -> Direction.Right
                        else -> Direction.RightBottom
                    }
            }
        }

        /**取包围该位置的所有数据*/
        private fun Circle.getSurrounds(): ArrayList<CircleD> {
            val surrounds = ArrayList<CircleD>()

            traverseSurrounds {
                surrounds.add(it)
                true
            }
            return surrounds
        }

        /**往该方向前进*/
        private fun CircleD.goDirection(action: (circleD: CircleD) -> Boolean) {
            circle.traverseSurrounds {
                if (it.direction == direction) {
                    if (action(it)) it.goDirection(action)
                    false
                } else true
            }
        }

        //******************************************************************************************
        private var oldType: Circle.TYPE = Circle.TYPE.White

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val circle = table[position]
            holder.run {
                //显示数据到界面
                tvCircle.text = if (circle.showText) circle.text else ""
                val size = rvTable.width / width
                itemView.layoutParams.width = size
                itemView.layoutParams.height = size
                rvTable.layoutParams.height = height * size
                tvTitle.text = "${oldType.alter.name1}下棋："


                tvCircle.setBackgroundResource(circle.type.resource)
                itemView.setBackgroundColor(Color.TRANSPARENT)
                tvCircle.setOnClickListener {
                    if (circle.show) return@setOnClickListener

                    oldType = oldType.alter
                    circle.show = true
                    circle.type = oldType
                    tableChecking(circle)
                    circle.notifyChange()
                }
            }
        }

        private val handler = Handler {
            val circle = it.obj as Circle
            circle.type = Circle.TYPE.Test
            circle.notifyChange()
            true
        }

        /**棋盘检查,检查哪方胜利*/
        private fun tableChecking(circle: Circle) {
            val surrounds = circle.getSurrounds()

            Thread {
                for (circleD in surrounds) {
                    val msg = Message()
                    msg.obj = circleD.circle
                    handler.sendMessage(msg)
                    Thread.sleep(100)

                    circleD.goDirection {
                        val msg = Message()
                        msg.obj = it.circle

                        handler.sendMessage(msg)
                        Thread.sleep(100)
                        true
                    }
                }
            }.start()


        }


    }

    //******************************************************************************************
    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCircle: TextView = itemView.findViewById(R.id.circle_tvTxt)
    }
}

//*************************************** Self Define **********************************************
open class Circle(
    var type: TYPE = TYPE.None,
    var show: Boolean = false,
    var showText: Boolean = false,
    var text: String = ""
) {
    override fun toString(): String = "type=$type  show=$show  showText=$showText  text=$text"

    enum class TYPE {
        None, White, Black, Test;

        override fun toString(): String = name

        val name1: String
            get() {
                return when (this) {
                    White -> "白棋"
                    Black -> "黑棋"
                    else -> "空"
                }
            }

        val alter: TYPE
            get() {
                return when (this) {
                    White -> Black
                    else -> White
                }
            }
    }
}

class CircleD(val circle: Circle, var direction: Direction) {

    override fun toString(): String = "direction = $direction"
}

enum class Direction {
    None,
    Left,
    Top,
    Right,
    Bottom,

    LeftTop,
    LeftBottom,
    RightTop,
    RightBottom
}
