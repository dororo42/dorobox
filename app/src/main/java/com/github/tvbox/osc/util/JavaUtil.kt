package com.github.tvbox.osc.util

import com.github.tvbox.osc.bean.LiveChannelGroup

/**
 *Automatic generated
 *@author Created by
 *@date 18/6/2024 19:11
 */
object JavaUtil {
    @JvmStatic
    fun findLiveLastChannel(liveChannelGroupList: List<LiveChannelGroup>): Pair<Int, Int> {
        val lastChannelName = HawkUtils.getLastLiveChannel()
        val lastChannelGroupName = HawkUtils.getLastLiveChannelGroup()
        return liveChannelGroupList.find { it.groupName == lastChannelGroupName }?.let {
            val channelItem =
                it.liveChannels.find { channelItem -> channelItem.channelName == lastChannelName }
            //如果该分组未查询到那么 走全局搜索
            if (channelItem == null) null else it.groupIndex to channelItem.channelIndex
        } ?: let {
            var noPassWordGroupIndex = -1
            // 注意：这里不能用 Kotlin forEach——它编译为 java.lang.Iterable#forEach
            // （API 24 默认方法），minSdk 21 的 Android 5/6 上直接 NoSuchMethodError
            for (group in liveChannelGroupList) {
                for (liceChannel in group.liveChannels) {
                    if (liceChannel.channelName == lastChannelName) {
                        return group.groupIndex to liceChannel.channelIndex
                    }
                    if (noPassWordGroupIndex == -1 && group.groupPassword.isEmpty()) {
                        noPassWordGroupIndex = group.groupIndex
                    }
                }
            }
            if (noPassWordGroupIndex == -1) noPassWordGroupIndex = 0
            noPassWordGroupIndex to 0
        }
    }


}