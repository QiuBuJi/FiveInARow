package com.example.five_in_a_row

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Point
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.TypedValue
import android.view.*
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    private lateinit var adapter: Adapter

    //region Companion
    companion object {
        lateinit var mTvTitle: TextView
        lateinit var mTvCircle: TextView
        val sizeScreen = Point()
        var heightMain: Int = 0
        var typeDefault: Circle.TYPE = Circle.TYPE.White
    }
    //endregion

    //region onCreate
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //提取控件到全局变量
        mTvTitle = main_tvTitle
        mTvCircle = main_tvCircle

        heightMain = main_clMain.height
        windowManager.defaultDisplay.getSize(sizeScreen)//取屏幕尺寸

        //init others

        main_btClear.setOnClickListener { swapAdapter(point) }//监听器，清空棋盘
        main_btClear.performClick()//初始化棋盘
    }

    /**交换adapter*/
    private fun swapAdapter(point: Point) {
        //设置棋盘适配器
        adapter = Adapter(this, main_rvTable, point.x, point.y)
        main_rvTable.swapAdapter(adapter, true)
        main_rvTable.layoutManager = GridLayoutManager(this, adapter.width)
    }

    override fun onStart() {
        super.onStart()

    }

    private val group1 = 1
    private val group2 = 2
    private val group3 = 3
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.run {
            addSubMenu(group1, 0, 0, "先下棋").run {
                add(group1, 1, 0, "白棋").setIcon(R.drawable.ic_bg_circle_white)
                add(group1, 2, 0, "黑棋").setIcon(R.drawable.ic_bg_circle_black)
            }

            addSubMenu(group2, 0, 0, "棋盘格数").run {
                add(group2, 1, 0, "6*6").setIcon(R.drawable.ic_bg_table)
                add(group2, 2, 0, "6*10").setIcon(R.drawable.ic_bg_table)
                add(group2, 3, 0, "10*10").setIcon(R.drawable.ic_bg_table)
                add(group2, 4, 0, "10*16").setIcon(R.drawable.ic_bg_table)
                add(group2, 5, 0, "16*16").setIcon(R.drawable.ic_bg_table)
                add(group2, 6, 0, "16*26").setIcon(R.drawable.ic_bg_table)
            }

            add(group3, 4, 0, "关于")
        }
        return super.onCreateOptionsMenu(menu)
    }

    private var point: Point = Point(8, 14)//初始棋盘格数
    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        item?.run {
            when (groupId) {
                //设置哪种类型的棋先下
                group1 -> {
                    when (itemId) {
                        1 -> adapter.currentType = Circle.TYPE.White
                        2 -> adapter.currentType = Circle.TYPE.Black
                    }
                }
                //设置棋盘格数
                group2 -> {
                    val split = title.split("*")
                    point = when (itemId) {
                        0    -> return@run
                        else -> Point(split[0].toInt(), split[1].toInt())
                    }
                    swapAdapter(point)
                }
                //跳转关于窗口
                group3 -> {
                    val intent = Intent(this@MainActivity, AboutActivity::class.java)
                    startActivity(intent)
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }
    //endregion

    //region Adapter
    //*************************************** Adapter **********************************************
    class Adapter(
        private val context: Context,
        private val rvTable: RecyclerView,
        val width: Int,
        val height: Int
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

            currentType = typeDefault//设置默认类型
            return Holder(from)
        }

        private var once: Boolean = false
        /**设置棋盘高度*/
        private fun onceSetHeight() {
            //只让它执行1次
            if (once) return
            once = true
            val tableHeight = rvTable.width / width * height
            val remain = heightMain - rvTable.y
            // FIXME: 2020.2.5 dynamic set the table height
//            if (size < remain) rvTable.layoutParams.height = size
        }

        override fun getItemCount(): Int = table.size

        //region Game Method
        //*********************************** Game Method ******************************************
        /**获取该类型的资源*/
        private val Circle.TYPE.resource: Int
            get() {
                return when (this) {
                    Circle.TYPE.Black -> R.drawable.ic_bg_circle_black
                    Circle.TYPE.White -> R.drawable.ic_bg_circle_white
                    Circle.TYPE.Test  -> R.drawable.ic_bg_circle_blue
                    else              -> R.drawable.ic_bg_rectangle
                }
            }

        /**获取该类型的颜色*/
        private val Circle.TYPE.color: Int
            get() {
                return when (this) {
                    Circle.TYPE.White -> Color.WHITE
                    Circle.TYPE.Black -> Color.BLACK
                    Circle.TYPE.Test  -> Color.BLUE
                    else              -> Color.YELLOW
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
                -1   ->
                    when (y) {
                        -1   -> Direction.LeftTop
                        0    -> Direction.Left
                        else -> Direction.LeftBottom
                    }
                0    ->
                    when (y) {
                        -1   -> Direction.Top
                        0    -> Direction.None
                        else -> Direction.Bottom
                    }
                else ->
                    when (y) {
                        -1   -> Direction.RightTop
                        0    -> Direction.Right
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
        private fun Circle.getLines(): ArrayList<ArrayList<Circle>> = getLines { true }

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

        //endregion
        //******************************************************************************************
        /**当前该下棋的类型*/  //这个初始化不管用
        var currentType: Circle.TYPE = typeDefault
            @SuppressLint("SetTextI18n")
            set(value) {
                field = value
                mTvTitle.text = "${value.name1}下棋："
                mTvCircle.setBackgroundResource(value.resource)
            }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.run {
                /**棋子框的边长*/
                val sizePieces = rvTable.width / width
                val circle = table[position]

                itemView.layoutParams.height = sizePieces//配置初始化数据

                //文字尺寸适配框大小
                tvCircle.run {
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePieces * 0.4f)
                    layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                    layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                }

                tvCircle.run {
                    text = circle.text
                    setTextColor(Color.BLACK)
//                    setTextColor(circle.textColor)
                    setBackgroundResource(circle.type.resource)
                    setOnClickListener {
                        //设置不能在同一个地方重复下棋
                        if (circle.show) return@setOnClickListener

                        //保存上一次，下的是什么棋
                        circle.show = true
                        circle.type = currentType
                        currentType = currentType.alter
                        typeDefault = currentType

                        //检查棋盘
                        tableChecking(circle)
                        //通知该位置刷新显示的数据
                        circle.notifyChange()
                    }
                }
            }
        }

        /**棋盘检查,检查哪方胜利*/
        private fun tableChecking(pCircle: Circle) {
            val detectWon = pCircle.detectWon()

            //有赢的数据，则执行赢程序
            detectWon?.let {
                Thread {
                    for (line in it) {
                        for (circle in line) {
                            circle.run {
                                text = circle.type.name1
                                textColor = circle.type.color
                                type = Circle.TYPE.Test
//                                text = "赢"
                                notifyChange()
                            }
                            Thread.sleep(100)
                        }
                    }
                }.start()
            }

        }
    }//**************************************** Adapter End ****************************************

    class Holder(rvParent: View) : RecyclerView.ViewHolder(rvParent) {
        val tvCircle: TextView = rvParent.findViewById(R.id.circle_tvTxt)
    }
    //endregion
}


//region Self Define
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
                    Test  -> "测试"
                    else  -> "空"
                }
            }

        /**改变为相反的类型棋子*/
        val alter: TYPE
            get() {
                return when (this) {
                    White -> Black
                    Black -> White
                    else  -> Test
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

    /**取反操作*/
    operator fun not(): Direction {
        return when (this) {
            Left        -> Right
            Top         -> Bottom
            LeftTop     -> RightBottom
            LeftBottom  -> RightTop

            Right       -> Left
            Bottom      -> Top
            RightBottom -> LeftTop
            RightTop    -> LeftBottom
            else        -> None
        }
    }

    override fun toString(): String {
        val strDirection = when (this) {
            Left        -> "←"
            Top         -> "↑"
            Right       -> "→"
            Bottom      -> "↓"
            LeftTop     -> "↖"
            LeftBottom  -> "↙"
            RightTop    -> "↗"
            RightBottom -> "↘"
            else        -> "㊣"
        }
        return "$strDirection  ($name)"
    }
}
//endregion