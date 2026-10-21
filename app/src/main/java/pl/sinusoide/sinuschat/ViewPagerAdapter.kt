package pl.sinusoide.sinuschat

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import pl.sinusoide.sinuschat.messenger.MessengerFragment
import pl.sinusoide.sinuschat.util.ExperimentalFeatures

class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private val aiEnabled = ExperimentalFeatures.isAiEnabled(activity)

    override fun getItemCount(): Int = if (aiEnabled) 4 else 3

    override fun createFragment(position: Int): Fragment {
        if (aiEnabled) {
            return when (position) {
                0    -> MessengerFragment()
                1    -> CallsFragment()
                2    -> SiniaOfflineFragment()
                else -> AboutFragment()
            }
        }
        return when (position) {
            0    -> MessengerFragment()
            1    -> CallsFragment()
            else -> AboutFragment()
        }
    }
}
