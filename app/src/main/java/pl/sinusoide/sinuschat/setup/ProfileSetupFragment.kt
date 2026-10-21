package pl.sinusoide.sinuschat.setup
import android.app.Activity
import android.content.Intent
import pl.sinusoide.sinuschat.util.optStringOrNull

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.crypto.ProfileCrypto
import pl.sinusoide.sinuschat.network.ApiClient
import pl.sinusoide.sinuschat.util.AppExecutor
import pl.sinusoide.sinuschat.util.AvatarHelper
import pl.sinusoide.sinuschat.util.CropActivity
import pl.sinusoide.sinuschat.util.FileUploader
import pl.sinusoide.sinuschat.util.PopupHelper

class ProfileSetupFragment : Fragment() {

    private var userEmail: String = ""
    private lateinit var inputName: EditText
    private lateinit var btnFinish: Button
    private lateinit var progress: ProgressBar
    private lateinit var avatarCircle: ImageView
    private lateinit var avatarImage: ImageView
    private lateinit var avatarContainer: View
    private lateinit var btnAddPhoto: TextView

    private var selectedAvatarUri: Uri? = null
    private var removeExistingAvatar = false
    private var onPhotoPicked: ((Uri) -> Unit)? = null

    private val cropImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val outputUri = result.data?.getStringExtra(CropActivity.EXTRA_OUTPUT)
            if (outputUri != null) {
                val uri = Uri.parse(outputUri)
                onPhotoPicked?.invoke(uri) ?: applyPhoto(uri)
            }
        }
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val intent = Intent(requireContext(), CropActivity::class.java)
            intent.putExtra(CropActivity.EXTRA_URI, uri)
            cropImage.launch(intent)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_setup_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userEmail = arguments?.getString("email", "") ?: ""

        inputName = view.findViewById(R.id.input_display_name)
        btnFinish = view.findViewById(R.id.btn_finish)
        progress = view.findViewById(R.id.progress)
        avatarCircle = view.findViewById(R.id.avatar_letter)
        avatarImage = view.findViewById(R.id.avatar_image)
        avatarContainer = view.findViewById(R.id.avatar_container)
        btnAddPhoto = view.findViewById(R.id.btn_add_photo)

        avatarImage.clipToOutline = true

        avatarContainer.setOnClickListener { showPhotoSheet() }
        avatarCircle.setOnClickListener { showPhotoSheet() }
        avatarImage.setOnClickListener { showPhotoSheet() }
        btnAddPhoto.setOnClickListener { pickImage.launch("image/*") }

        updateLetterAvatar(AvatarHelper.getMyColor(requireContext()))

        inputName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val name = s.toString().trim()
                btnFinish.isEnabled = name.length >= 2
                if (avatarImage.visibility != View.VISIBLE) {
                    updateLetterAvatar(AvatarHelper.getMyColor(requireContext()))
                }
            }
        })

        btnFinish.setOnClickListener { finishSetup() }
        checkExistingProfile()

        listOf(
            view.findViewById<View>(R.id.profile_title),
            view.findViewById<View>(R.id.profile_subtitle),
            avatarContainer, btnAddPhoto, inputName, btnFinish
        ).forEachIndexed { i, v ->
            if (v == null) return@forEachIndexed
            v.alpha = 0f; v.translationY = 30f
            v.animate().alpha(1f).translationY(0f).setDuration(400).setStartDelay(100L + i * 80L).start()
        }
    }

    private fun checkExistingProfile() {
        val emailHash = SetupActivity.accountHashOf(userEmail)
        val app = requireContext().applicationContext
        pl.sinusoide.sinuschat.util.AppExecutor.io {
            val proof = pl.sinusoide.sinuschat.network.ApiClient.getAuthProof(app)
            val user = pl.sinusoide.sinuschat.network.ApiClient.getObject("/api/user/$emailHash", app,
                proof?.let { mapOf("X-Auth-Proof" to it) })
            val savedName = ProfileCrypto.openOwnName(app, user?.optStringOrNull("display_name"))
            val savedColor = user?.optStringOrNull("avatar_color")
            val avatarUrl = user?.optStringOrNull("avatar_url")
            val avatarPath = if (!avatarUrl.isNullOrBlank() && avatarUrl != "null" &&
                AvatarHelper.getLocalAvatarPath(app) == null)
                AvatarHelper.downloadAvatar(app, avatarUrl) else null

            AppExecutor.main {
                if (!isAdded) return@main
                if (savedColor != null && savedColor.startsWith("#") && !AvatarHelper.hasMyColor(app)) {
                    AvatarHelper.setMyColor(app, savedColor)
                }
                if (!savedName.isNullOrBlank()) {
                    inputName.setText(savedName)
                    updateLetterAvatar(AvatarHelper.getMyColor(requireContext()))
                    view?.findViewById<TextView>(R.id.profile_subtitle)?.text =
                        getString(R.string.profile_exists_format, savedName)
                }
                if (avatarPath != null) {
                    SetupActivity.setUserAvatarUri(app, avatarPath)
                    if (AvatarHelper.loadAvatarInto(requireContext(), avatarImage)) {
                        avatarImage.visibility = View.VISIBLE
                        avatarCircle.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun currentPhoto(): Any? = selectedAvatarUri
        ?: AvatarHelper.getLocalAvatarPath(requireContext())
            ?.takeIf { avatarImage.visibility == View.VISIBLE }?.let { java.io.File(it) }

    private fun showPhotoSheet() {
        val ctx = requireContext()
        val originalColor = AvatarHelper.getMyColor(ctx)
        pl.sinusoide.sinuschat.ui.ProfilePhotoSheet.show(
            requireActivity(), inputName.text.toString().trim(), originalColor, currentPhoto(),
            pickPhoto = { onPicked -> onPhotoPicked = onPicked; pickImage.launch("image/*") },
            onDismiss = { onPhotoPicked = null }
        ) { color, photo, remove ->
            if (!isAdded) return@show
            if (!color.equals(originalColor, ignoreCase = true)) AvatarHelper.setMyColor(ctx, color)
            when {
                photo != null -> applyPhoto(photo)
                remove -> removePhoto()
                else -> updateLetterAvatar(AvatarHelper.getMyColor(ctx))
            }
        }
    }

    private fun applyPhoto(uri: Uri) {
        selectedAvatarUri = uri
        avatarImage.setImageURI(uri)
        avatarImage.visibility = View.VISIBLE
        avatarCircle.visibility = View.GONE
    }

    private fun removePhoto() {
        if (selectedAvatarUri == null && AvatarHelper.getLocalAvatarPath(requireContext()) != null) {
            AvatarHelper.deleteLocalAvatar(requireContext())
            removeExistingAvatar = true
        }
        selectedAvatarUri = null
        avatarImage.setImageDrawable(null)
        avatarImage.visibility = View.GONE
        avatarCircle.visibility = View.VISIBLE
        updateLetterAvatar(AvatarHelper.getMyColor(requireContext()))
    }

    private fun updateLetterAvatar(colorHex: String) {
        val name = inputName.text.toString().trim()
        val letter = AvatarHelper.initialOf(name)
        val dp = resources.displayMetrics.density
        val size = (100 * dp).toInt()
        avatarCircle.setImageBitmap(AvatarHelper.createLetterBitmap(letter, colorHex, size))
    }

    private fun finishSetup() {
        val displayName = inputName.text.toString().trim()
        if (displayName.length < 2) return

        progress.visibility = View.VISIBLE
        btnFinish.isEnabled = false

        val emailHash = SetupActivity.accountHashOf(userEmail)

        if (selectedAvatarUri != null) {
            val localPath = AvatarHelper.saveAvatarLocally(requireContext(), selectedAvatarUri!!)
            if (localPath != null) SetupActivity.setUserAvatarUri(requireContext(), localPath)

            FileUploader.uploadAvatarAsync(requireContext(), selectedAvatarUri!!, emailHash) { remoteUrl ->
                if (remoteUrl == null) {
                    Log.e("ProfileSetup", "Avatar upload failed — kontynuuję bez avatara")
                }
                saveProfile(emailHash, displayName, remoteUrl?.let { AvatarHelper.versionedUrl(it) })
            }
        } else {
            saveProfile(emailHash, displayName, null)
        }
    }

    private fun saveProfile(emailHash: String, displayName: String, remoteAvatarUrl: String?) {
        val ctx = requireContext().applicationContext
        pl.sinusoide.sinuschat.util.AppExecutor.io {
            val result = AccountRegistration.register(ctx, userEmail, displayName, remoteAvatarUrl)
            AppExecutor.main {
                if (!isAdded) return@main
                progress.visibility = View.GONE
                when (result) {
                    is AccountRegistration.Result.Ok -> {
                        if (removeExistingAvatar && remoteAvatarUrl == null) AppExecutor.io {
                            ApiClient.post("/api/user/update", org.json.JSONObject().put("avatarUrl", ""), ctx)
                        }
                        SetupActivity.setWizardStep(ctx, SetupStepFragment.Step.NOTIFY.name)
                        (activity as? SetupActivity)?.startWizard(SetupStepFragment.Step.NOTIFY)
                    }
                    is AccountRegistration.Result.NeedsCode -> {
                        btnFinish.isEnabled = true
                        PopupHelper.showError(requireActivity(), getString(R.string.setup_code_expired))
                        (activity as? SetupActivity)?.navigateTo(EmailInputFragment())
                    }
                    is AccountRegistration.Result.Failed -> {
                        btnFinish.isEnabled = true
                        PopupHelper.showError(requireActivity(), getString(R.string.error_server_connect))
                    }
                }
            }
        }
    }
}
