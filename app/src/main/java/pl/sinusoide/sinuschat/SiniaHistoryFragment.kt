package pl.sinusoide.sinuschat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import pl.sinusoide.sinuschat.util.PopupHelper
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SiniaHistoryFragment : Fragment() {

    companion object {
        fun newInstance(onOpen: (Conversation) -> Unit): SiniaHistoryFragment =
            SiniaHistoryFragment().also { it.onOpenCallback = onOpen }
    }

    private var onOpenCallback: ((Conversation) -> Unit)? = null
    private lateinit var adapter: ConversationAdapter
    private lateinit var emptyLayout: View

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_sinia_history, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).top
            v.setPadding(v.paddingLeft, top, v.paddingRight, v.paddingBottom)
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(view)

        emptyLayout = view.findViewById(R.id.layout_history_empty)
        val rv      = view.findViewById<RecyclerView>(R.id.rv_history)

        adapter = ConversationAdapter(
            onOpen = { conv ->
                onOpenCallback?.invoke(conv)
                closeDrawer()
            },
            onDelete = { conv ->
                val parent   = parentFragment as? SiniaOfflineFragment
                val isCurrent = parent?.currentConvId == conv.id

                ConversationManager.delete(requireContext(), conv.id)
                adapter.remove(conv.id)
                checkEmpty()
                PopupHelper.show(requireActivity(), getString(R.string.deleted_dot))

                if (isCurrent) {
                    closeDrawer()
                    parent.startNewChat()
                }
            }
        )

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter        = adapter

        view.findViewById<ImageButton>(R.id.btn_history_back).setOnClickListener { closeDrawer() }

        view.findViewById<ImageButton>(R.id.btn_history_new).setOnClickListener {
            closeDrawer()
            (parentFragment as? SiniaOfflineFragment)?.startNewChat()
        }

        loadConversations()
    }

    private fun loadConversations() {
        val modelId = (parentFragment as? SiniaOfflineFragment)?.activeModelId()
        val list = ConversationManager.loadAll(requireContext(), modelId)
        adapter.setItems(list)
        checkEmpty()
    }

    private fun checkEmpty() {
        emptyLayout.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    private fun closeDrawer() {
        (parentFragment as? SiniaOfflineFragment)
            ?.view?.findViewById<DrawerLayout>(R.id.drawer_layout)
            ?.closeDrawer(android.view.Gravity.START)
    }
}
