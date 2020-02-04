package com.example.five_in_a_row

import android.content.Context
import android.graphics.Color
import android.graphics.Point
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
        lateinit var mTvCircle: TextView
        val sizeScreen = Point()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTitle = main_tvTitle
        mTvCircle = main_tvCircle

        windowManager.defaultDisplay.getSize(sizeScreen)

        val adapter = Adapter(this, main_rvTable)
        main_rvTable.adapter = adapter
        main_rvTable.layoutManager = GridLayoutManager(this, adapter.width)

        //清空棋盘
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
            onceSetHeight()
            return Holder(from)
        }

        var once: Boolean = false
        /**设置棋盘高度*/
        private fun onceSetHeight() {
            if (once) return
            once = true
            val size = (rvTable.width / width) * height
//            val remain = sizeScreen.y - rvTable.y
            // FIXME: 2020.2.5 dynamic set the table height
//            if (size < remain) rvTable.layoutParams.height = size
        }

        override fun getItemCount(): Int = table.size

        //*********************************** Game Method ******************************************
        /**获取该类型的资源*/
        private val Circle.TYPE.resource: Int
            get() {
                return when (this) {
                    Circle.TYPE.Black -> R.drawable.ic_bg_circle_black
                    Circle.TYPE.White -> R.drawable.ic_bg_circle_white
                    Circle.TYPE.Test -> R.drawable.ic_bg_circle_blue
                    else -> R.drawable.ic_bg_rectangle
                }
            }

        /**通知数据改变*/
        private val handler = Handler {
            if (it.arg1 >= 0) notifyItemChanged(it.arg1)
            true
        }

        /**通知数据改变了*/
        private fun Circle.notifyChange() {
            val position = table.indexOf(this)
            val msg = Message()
            msg.arg1 = position
            handler.sendMessage(msg)
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
        private fun CircleD.traverseDirection(action: (circleD: CircleD) -> Boolean) {
            if (action(this)) {

                circle.traverseSurrounds {
                    if (it.direction == direction) {
                        it.traverseDirection(action)
                        false
                    } else true
                }
            }
        }

        /**取所有通过该点的直线*/
        private fun Circle.getLines(): ArrayList<ArrayList<Circle>> {
            return getLines { true }
        }

        /**取所有通过该点的直线*/
        private fun Circle.getLines(action: (circleD: CircleD) -> Boolean): ArrayList<ArrayList<Circle>> {
            val lines = ArrayList<ArrayList<CircleD>>()
            val linesOut = ArrayList<ArrayList<Circle>>()

            //收集该点发散出去的所有直线
            traverseSurrounds { it ->
                val line = ArrayList<CircleD>()
                //收集棋子为直线
                it.traverseDirection {
                    if (action(it)) {
                        line.add(it)
                        true
                    } else false
                }
                if (line.isNotEmpty()) lines.add(line)//不要空的
                true
            }

            //融合该直线与直线延长线上的直线
            while (lines.isNotEmpty()) {
                val line = lines.removeAt(0)
                val not = !line[0].direction//相反的直线类型
                val circles = ArrayList<Circle>()

                line.reverse()//反向
                for (circleD in line) circles.add(circleD.circle)

                if (lines.isEmpty()) circles.add(this)//添加自己
                for (lineTemp in lines) {
                    if (lineTemp[0].direction == not) {
                        circles.add(this)//添加自己
                        for (circleD in lineTemp) circles.add(circleD.circle)//添加剩下的
                        lines.remove(lineTemp)
                        break
                    }
                }
                linesOut.add(circles)
            }
            return linesOut
        }

        /**检测赢的一方*/
        private fun Circle.detectWon(): ArrayList<ArrayList<Circle>>? {
            val linesOut = ArrayList<ArrayList<Circle>>()
            val lines = getLines { it.circle.type == this.type }

            //大于等于5颗棋子在一条直线上，判断为赢
            for (line in lines) if (line.size >= 5) linesOut.add(line)
            return if (linesOut.isNotEmpty()) linesOut else null
        }

        //******************************************************************************************
        private var oldType: Circle.TYPE = Circle.TYPE.White

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.run {
                val circle = table[position]

                //显示数据到界面
                tvCircle.text = circle.text
                val size = rvTable.width / width
                itemView.layoutParams.width = size
                itemView.layoutParams.height = size
                tvTitle.text = "${oldType.alter.name1}下棋："

                tvCircle.setTextColor(circle.textColor)
                tvCircle.setBackgroundResource(circle.type.resource)
                mTvCircle.setBackgroundResource(oldType.alter.resource)
                itemView.setBackgroundColor(Color.TRANSPARENT)
                tvCircle.setOnClickListener {
                    //设置不能在同一个地方重复下棋
                    if (circle.show) return@setOnClickListener

                    //保存上一次，下的是什么棋
                    oldType = oldType.alter
                    circle.show = true
                    circle.type = oldType

                    //检查棋盘
                    tableChecking(circle)
                    //通知该位置刷新显示的数据
                    circle.notifyChange()
                }
            }
        }

        /**棋盘检查,检查哪方胜利*/
        private fun tableChecking(circle: Circle) {
            val detectWon = circle.detectWon()

            //有赢的数据，则执行赢程序
            detectWon?.let {
                Thread {
                    for (line in it) {
                        for (circle in line) {
                            circle.text = circle.type.name1
                            circle.textColor =
                                if (circle.type == Circle.TYPE.White) Color.WHITE else Color.BLACK
                            circle.type = Circle.TYPE.Test
//                            circle.text = "赢"
                            circle.notifyChange()
                            Thread.sleep(100)
                        }
                    }
                }.start()
            }

        }
    }//**************************************** Adapter End ****************************************

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCircle: TextView = itemView.findViewById(R.id.circle_tvTxt)
    }
}

//*************************************** Self Define **********************************************
open class Circle(
    var textColor: Int = Color.WHITE,
    var type: TYPE = TYPE.None,
    var show: Boolean = false,
    var text: String = ""
) {

    override fun toString(): String = "type=$type  show=$show  text=\"$text\""

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
    RightBottom;

    operator fun not(): Direction {
        return when (this) {
            Left -> Right
            Top -> Bottom
            LeftTop -> RightBottom
            LeftBottom -> RightTop

            Right -> Left
            Bottom -> Top
            RightBottom -> LeftTop
            RightTop -> LeftBottom
            else -> None
        }
    }
}
