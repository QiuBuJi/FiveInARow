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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_main.*

const val TAG = "msg_mine"

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
            for (index in 0 until size) table.add(Circle().apply { this.index = index })
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
        private val handler = Handler { it ->
            it.obj?.let {
                (it as () -> Unit).invoke()
            }
            if (it.arg1 >= 0) notifyItemChanged(it.arg1)
            true
        }

        private fun Handler.send(action: () -> Unit) {
            val msg = Message()
            msg.obj = action
            handler.sendMessage(msg)
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
        private fun Circle.getSurrounds(action: (CircleD) -> Boolean = { true }): ArrayList<CircleD> {
            val surrounds = ArrayList<CircleD>()

            traverseSurrounds {
                if (action(it)) surrounds.add(it)
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

        private fun CircleD.getDirectionLine(action: (circleD: CircleD) -> Boolean = { it.circle.type != Circle.TYPE.None }): ArrayList<CircleD> {
            val line = ArrayList<CircleD>()
            //收集棋子为直线
            traverseDirection {
                if (action(it)) {
                    line.add(it)
                    true
                } else false
            }
            return line
        }

        /**取所有通过该点的直线*/
        private fun Circle.getLines(action: (circleD: CircleD) -> Boolean = { true }): ArrayList<ArrayList<Circle>> {
            val lines = ArrayList<ArrayList<CircleD>>()

            //收集该点发散出去的所有直线
            traverseSurrounds {
                //收集棋子为直线
                val line = it.getDirectionLine(action)

                if (line.isNotEmpty()) lines.add(line)//不要空的
                true
            }

            return connectLines(lines)
        }

        private fun Circle.connectLines(
            lines: ArrayList<ArrayList<CircleD>>,
            mark: Boolean = false
        ): ArrayList<ArrayList<Circle>> {
            val linesOut = ArrayList<ArrayList<Circle>>()

            var dirS = 0
            fun convertToCircle(line: ArrayList<CircleD>): ArrayList<Circle> {
                return ArrayList<Circle>().apply {
                    for (circleD in line) {
                        if (mark) {
                            dirS = circleD.circle.direS_int or circleD.direction.direS.value
                            circleD.circle.direS_int = dirS
                        }
                        add(circleD.circle)
                    }
                }
            }

            //融合该直线与直线延长线上的直线
            while (lines.isNotEmpty()) {
                val lineConcat = ArrayList<Circle>()
                val line = lines.removeAt(0)
                val not = !line[0].direction//相反的直线类型

                line.reverse()//反向
                lineConcat.addAll(convertToCircle(line))
                lineConcat.add(this)//添加自己
                direS_int = dirS or direS_int

                for (lineTemp in lines) {
                    if (lineTemp[0].direction == not) {
                        lineConcat.addAll(convertToCircle(lineTemp))//添加剩下的
                        lines.remove(lineTemp)
                        break
                    }
                }
                if (line.isEmpty()) this.direS_int = dirS
                linesOut.add(lineConcat)
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

        /**获取所有大于1的五子棋直线*/
        private fun getTableLines(): ArrayList<ArrayList<Circle>> {
            val lines = ArrayList<ArrayList<Circle>>()

            //遍历整个棋盘
            for (currCircle in table) {
                if (currCircle.type == Circle.TYPE.None) continue//避开空格子

                val linesTemp = ArrayList<ArrayList<CircleD>>()
                //取currCircle周围棋子为currCircle.type类型的数据
                val surrounds = currCircle.getSurrounds { it.circle.type == currCircle.type }

                for (surround in surrounds) {
                    //数据被用则不再使用
                    val state = surround.direction.direS.value and surround.circle.direS_int
                    if (state > 0) continue

                    //取周围该点的直线数据
                    val line = surround.getDirectionLine { it.circle.type == currCircle.type }
                    if (line.isNotEmpty()) linesTemp.add(line)//不要空的
                }
                //连接直线
                val connectLines = currCircle.connectLines(linesTemp, true)
                lines.addAll(connectLines)
            }
            for (circle in table) circle.direS_int = 0
            return lines
        }

        private var lines: ArrayList<ArrayList<Circle>> = ArrayList()
        private fun detect(circle: Circle) {
            if (lines.isNotEmpty()) {
                for (line in lines) {
                    for (circle in line) circle.tvText1?.text = ""
                }
            }

            lines = circle.getLines { it.circle.type == circle.type }
            if (lines.isEmpty()) return

            for (line in lines) {
                for (circle in line) {
                    circle.tvText1?.text = circle.direS_int.toString()
                    circle.tvText1?.setTextColor(circle.type.alter.color)
                }
            }
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
                val sizeReal = sizePieces * 0.4f
                val circle = table[position]

                itemView.layoutParams.height = sizePieces//配置初始化数据
                circle.tvText = tvCircle
                circle.tvText1 = tvText
                tvText.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizeReal)

                tvCircle.run {
                    //文字尺寸适配框大小
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, sizeReal)
                    layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                    layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT

                    text = circle.text
                    setTextColor(circle.textColor)
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

                //显示下标
                tvText3.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePieces * 0.16f)
                tvText3.text = position.toString()

                tvCircle.setOnLongClickListener {
                    val tableData = getTableLines()

//                    detect(circle)
                    if (tableData.isNotEmpty()) {
                        Thread {
                            for (line in tableData) {
                                for (circle in line) {
                                    handler.send {
                                        circle.tvText1?.text = "●"
                                        circle.tvText1?.setTextColor(circle.type.alter.color)
                                    }
                                }

                                val l: Long = 500
                                Thread.sleep(l)
                                for (circle in line) {
                                    handler.send {
                                        circle.tvText1?.text = ""
                                        circle.tvText1?.setTextColor(circle.type.color)
                                    }
                                }
                                Thread.sleep(l)
                            }

                        }.start()
                    }

                    true
                }
            }
        }

        /**给直线每个格子设置文字*/
        private fun insertText(line: ArrayList<Circle>, txt: String): Unit {
            val iterator = txt.iterator()

            for (circle in line) {
                circle.run {
                    val strNext =
                        if (iterator.hasNext()) iterator.nextChar().toString() else ""
                    text = strNext
                }
            }
        }

        /**棋盘检查,检查哪方胜利*/
        private fun tableChecking(pCircle: Circle) {
            val detectWon = pCircle.detectWon()

            val strWin = "太棒了你！！！！！"
            val iterator = strWin.iterator()

            detectWon?.let {
                Thread {
                    //有赢的数据，则执行赢程序
                    for (line in it) {
                        for (circle in line) {
                            circle.run {
                                val strNext =
                                    if (iterator.hasNext()) iterator.nextChar().toString() else ""
                                text = strNext
                                textColor = circle.type.color
                                type = Circle.TYPE.Test
                                notifyChange()
                            }
                            Thread.sleep(100)
                        }
                    }

                    //贪吃蛇游戏代码部分
                    if (it.size > 1) return@Thread
                    val line = it[0]
                    var direction: Direction = Direction.Right

                    while (true) {
                        var head = line.first()
                        head.textColor = Color.RED

                        val blanks = head.getSurrounds { it.circle.type == Circle.TYPE.None }
                        var random = try {
                            blanks.random()
                        } catch (e: Exception) {
                            for (circle in line) {
                                circle.run {
                                    textColor = Color.RED
                                    notifyChange()
                                }
                            }
                            insertText(line, "哦噢我死了")
                            Thread.sleep(2000)

                            for (circle in line) {
                                circle.run {
                                    clear()
                                    notifyChange()
                                }
                            }
                            break
                        }
                        for (blank in blanks) if (blank.direction == direction) random = blank

                        random.circle.copy(head)
                        random.circle.notifyChange()
                        direction = random.direction

                        var temp = head
                        head = random.circle
                        for (index in 1 until line.size) {
                            val circle = line[index]

                            temp.copy(circle)
                            temp.notifyChange()
                            temp = circle
                        }

                        val last = line.last()
                        last.clear()
                        last.notifyChange()
                        line.remove(last)
                        line.add(0, head)

                        Thread.sleep(500)
                    }

                }.start()
            }

        }
    }
    //**************************************** Adapter End ****************************************

    class Holder(rvParent: View) : RecyclerView.ViewHolder(rvParent) {
        val tvCircle: TextView = rvParent.findViewById(R.id.circle_tvTxt)
        val tvText: TextView = rvParent.findViewById(R.id.circle_tvTxt2)
        val tvText3: TextView = rvParent.findViewById(R.id.circle_tvTxt3)
    }
    //endregion
}


//region Self Define
//*************************************** Self Define **********************************************
open class Circle(
    var textColor: Int = Color.WHITE,
    var type: TYPE = TYPE.None,
    var show: Boolean = false,
    var text: String = "",
    var direS_int: Int = 0,
    var tvText: TextView? = null,
    var tvText1: TextView? = null,
    var index: Int = -1
) {

    /**拷贝数据到本类中*/
    fun copy(circle: Circle) {
        textColor = circle.textColor
        type = circle.type
        show = circle.show
        text = circle.text
        direS_int = circle.direS_int
    }

    /**清空本类的数据*/
    fun clear() {
        textColor = 0
        type = TYPE.None
        show = false
        text = ""
        direS_int = 0
    }

    override fun toString(): String = "index=$index  type=$type  show=$show  text=\"$text\""

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

enum class DirectionS(val value: Int) {
    /**─*/
    Horizontal(1),
    /**│*/
    Vertical(2),
    /**╲*/
    CantLeft(4),
    /**╱*/
    CantRight(8),
    /**╳*/
    None(0);

    override fun toString(): String = "$name1  binary=$strBinary"

    val strBinary: String
        get() {
            var num = value
            val sb = StringBuffer()
            while (num != 0) {
                val i = num % 2
                sb.insert(0, i)
                num /= 2
            }
            return sb.toString()
        }

    val name1: String
        get() {
            return when (this) {
                Horizontal -> "─"
                Vertical   -> "│"
                CantLeft   -> "╲"
                CantRight  -> "╱"
                else       -> "╳"
            }
        }
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

    val direS: DirectionS
        get() {
            return when (this) {
                Left, Right          -> DirectionS.Horizontal
                Top, Bottom          -> DirectionS.Vertical
                LeftTop, RightBottom -> DirectionS.CantLeft
                LeftBottom, RightTop -> DirectionS.CantRight
                else                 -> DirectionS.None
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