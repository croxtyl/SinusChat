package pl.sinusoide.sinuschat.messenger
import pl.sinusoide.sinuschat.util.optStringOrNull

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.network.ApiClient
import pl.sinusoide.sinuschat.util.AppExecutor
import pl.sinusoide.sinuschat.util.AvatarHelper
import pl.sinusoide.sinuschat.util.PopupHelper

class CreateGroupActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(pl.sinusoide.sinuschat.util.LocaleManager.wrap(newBase))
    }


    private val selectedMembers = mutableListOf<Pair<String, String>>()
    private lateinit var etName: EditText
    private lateinit var etSearch: EditText
    private lateinit var membersContainer: LinearLayout
    private lateinit var searchResults: LinearLayout
    private var selectedColor = AvatarHelper.PALETTE[0]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUI()
    }

    private fun buildUI() {
        val dp = resources.displayMetrics.density
        val root = android.widget.ScrollView(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#0F1923"))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16*dp).toInt(), (16*dp).toInt(), (16*dp).toInt(), (16*dp).toInt())
        }
        root.addView(content)
        setContentView(root)

        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (16*dp).toInt())

            addView(android.widget.ImageButton(this@CreateGroupActivity).apply {
                setImageResource(R.drawable.ic_back)
                setBackgroundResource(0)
                setColorFilter(android.graphics.Color.WHITE)
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams((40*dp).toInt(), (40*dp).toInt()))

            addView(android.widget.TextView(this@CreateGroupActivity).apply {
                text = getString(R.string.new_group)
                textSize = 20f
                setTextColor(android.graphics.Color.WHITE)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding((12*dp).toInt(), 0, 0, 0)
            })
        })

        val avatarView = android.widget.ImageView(this).apply {
            val bmp = AvatarHelper.createLetterBitmap("G", selectedColor, (72*dp).toInt())
            setImageBitmap(bmp)
        }
        val avatarRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (16*dp).toInt())
        }
        avatarRow.addView(avatarView, (72*dp).toInt().let {
            LinearLayout.LayoutParams(it, it).apply { marginEnd = (16*dp).toInt() }
        })
        avatarRow.addView(android.widget.Button(this).apply {
            text = getString(R.string.change_color)
            setBackgroundColor(android.graphics.Color.parseColor("#1E2D3D"))
            setTextColor(android.graphics.Color.parseColor("#5288C1"))
            setOnClickListener {
                AvatarHelper.showColorPicker(this@CreateGroupActivity, selectedColor) { color ->
                    selectedColor = color
                    val bmp = AvatarHelper.createLetterBitmap("G", color, (72*dp).toInt())
                    avatarView.setImageBitmap(bmp)
                }
            }
        })
        content.addView(avatarRow)

        content.addView(android.widget.TextView(this).apply {
            text = getString(R.string.group_name)
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#AAAAAA"))
            setPadding(0, 0, 0, (4*dp).toInt())
        })
        etName = EditText(this).apply {
            hint = getString(R.string.enter_name_hint)
            setHintTextColor(android.graphics.Color.parseColor("#666666"))
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#1A2635"))
            setPadding((12*dp).toInt(), (10*dp).toInt(), (12*dp).toInt(), (10*dp).toInt())
            textSize = 16f
        }
        content.addView(etName, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = (16*dp).toInt()
        })

        content.addView(android.widget.TextView(this).apply {
            text = getString(R.string.add_participants)
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#AAAAAA"))
            setPadding(0, 0, 0, (4*dp).toInt())
        })
        etSearch = EditText(this).apply {
            hint = "Szukaj po nazwie..."
            setHintTextColor(android.graphics.Color.parseColor("#666666"))
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#1A2635"))
            setPadding((12*dp).toInt(), (10*dp).toInt(), (12*dp).toInt(), (10*dp).toInt())
            textSize = 14f
        }
        content.addView(etSearch, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = (8*dp).toInt()
        })

        searchResults = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(searchResults)

        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val q = s?.toString()?.trim() ?: ""
                if (q.length >= 2) searchUsers(q) else searchResults.removeAllViews()
            }
        })

        content.addView(android.widget.TextView(this).apply {
            text = getString(R.string.selected_zero)
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#AAAAAA"))
            setPadding(0, (16*dp).toInt(), 0, (4*dp).toInt())
        }.also {
            it.tag = "selected_label"
        })
        membersContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(membersContainer)

        content.addView(android.widget.Button(this).apply {
            text = getString(R.string.create_group)
            textSize = 16f
            setBackgroundColor(android.graphics.Color.parseColor("#5288C1"))
            setTextColor(android.graphics.Color.WHITE)
            setPadding((16*dp).toInt(), (14*dp).toInt(), (16*dp).toInt(), (14*dp).toInt())
            setOnClickListener { createGroup() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (24*dp).toInt() })
    }

    private fun searchUsers(query: String) {
        AppExecutor.io {
            val results = ApiClient.getArray(
                "/api/users/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}", this
            ) ?: return@io
            AppExecutor.main {
                searchResults.removeAllViews()
                val dp = resources.displayMetrics.density
                for (i in 0 until results.length()) {
                    val user = results.getJSONObject(i)
                    val hash = user.optString("email_hash")
                    val name = pl.sinusoide.sinuschat.util.ContactCache.display(this, hash,
                        pl.sinusoide.sinuschat.crypto.ProfileCrypto.openName(this, hash, user.optStringOrNull("display_name"))) ?: ""
                    val color = AvatarHelper.colorForHash(hash)

                    if (selectedMembers.any { it.first == hash }) continue

                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding((8*dp).toInt(), (8*dp).toInt(), (8*dp).toInt(), (8*dp).toInt())
                        setBackgroundColor(android.graphics.Color.parseColor("#1A2635"))
                    }

                    val avatar = android.widget.ImageView(this).apply {
                        val bmp = AvatarHelper.createLetterBitmap(AvatarHelper.initialOf(name), color, (40*dp).toInt())
                        setImageBitmap(bmp)
                    }
                    row.addView(avatar, (40*dp).toInt().let {
                        LinearLayout.LayoutParams(it, it).apply { marginEnd = (12*dp).toInt() }
                    })

                    row.addView(android.widget.TextView(this).apply {
                        text = name
                        textSize = 15f
                        setTextColor(android.graphics.Color.WHITE)
                    }, LinearLayout.LayoutParams(0, -2, 1f))

                    row.addView(android.widget.ImageButton(this).apply {
                        setImageResource(android.R.drawable.ic_input_add)
                        setBackgroundResource(0)
                        setColorFilter(android.graphics.Color.parseColor("#5288C1"))
                        setOnClickListener {
                            addMember(hash, name)
                            searchResults.removeAllViews()
                            etSearch.setText("")
                        }
                    })

                    searchResults.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                        bottomMargin = (4*dp).toInt()
                    })
                }
            }
        }
    }

    private fun addMember(hash: String, name: String) {
        if (selectedMembers.any { it.first == hash }) return
        selectedMembers.add(Pair(hash, name))
        refreshMembersList()
    }

    private fun refreshMembersList() {
        val dp = resources.displayMetrics.density
        membersContainer.removeAllViews()
        for ((hash, name) in selectedMembers) {
            val color = AvatarHelper.colorForHash(hash)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding((8*dp).toInt(), (6*dp).toInt(), (8*dp).toInt(), (6*dp).toInt())
            }
            val avatar = android.widget.ImageView(this).apply {
                val bmp = AvatarHelper.createLetterBitmap(AvatarHelper.initialOf(name), color, (36*dp).toInt())
                setImageBitmap(bmp)
            }
            row.addView(avatar, (36*dp).toInt().let {
                LinearLayout.LayoutParams(it, it).apply { marginEnd = (10*dp).toInt() }
            })
            row.addView(android.widget.TextView(this).apply {
                text = name; textSize = 14f
                setTextColor(android.graphics.Color.WHITE)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(android.widget.ImageButton(this).apply {
                setImageResource(android.R.drawable.ic_delete)
                setBackgroundResource(0)
                setColorFilter(android.graphics.Color.parseColor("#FF4444"))
                setOnClickListener {
                    selectedMembers.removeAll { it.first == hash }
                    refreshMembersList()
                }
            })
            membersContainer.addView(row)
        }
    }

    private fun createGroup() {
        val name = etName.text.toString().trim()
        if (name.isEmpty()) {
            PopupHelper.show(this, getString(R.string.enter_group_name)); return
        }
        if (selectedMembers.isEmpty()) {
            PopupHelper.show(this, getString(R.string.add_min_one)); return
        }

        GroupRepository.createGroup(
            context     = this,
            name        = name,
            memberHashes = selectedMembers.map { it.first },
            avatarColor = selectedColor
        ) { groupId ->
            if (groupId == null) {
                PopupHelper.showError(this, getString(R.string.error_create_group))
                return@createGroup
            }
            startActivity(Intent(this, ChatActivity::class.java).apply {
                putExtra("conversationId", groupId)
                putExtra("otherUserName", name)
                putExtra("otherUserHash", "")
                putExtra("isGroup", true)
                putExtra("weStarted", true)
            })
            finish()
        }
    }
}
