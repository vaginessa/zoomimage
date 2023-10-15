/*
 * Copyright (C) 2023 panpf <panpfpanpf@outlook.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.panpf.zoomimage.sample.ui.common.view.menu

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.github.panpf.zoomimage.sample.databinding.DropdownMenuItemBinding
import com.github.panpf.zoomimage.sample.ui.base.view.MyBindingItemFactory

class DropdownMenuItemFactory(private val activity: Activity) :
    MyBindingItemFactory<DropdownMenu, DropdownMenuItemBinding>(DropdownMenu::class) {

    override fun initItem(
        context: Context,
        binding: DropdownMenuItemBinding,
        item: BindingItem<DropdownMenu, DropdownMenuItemBinding>
    ) {
        binding.root.setOnClickListener {
            val data = item.dataOrThrow
            showDialog(data) {
                binding.dropdownMenuItemInfoText.text = data.getValue()
            }
        }
    }

    override fun bindItemData(
        context: Context,
        binding: DropdownMenuItemBinding,
        item: BindingItem<DropdownMenu, DropdownMenuItemBinding>,
        bindingAdapterPosition: Int,
        absoluteAdapterPosition: Int,
        data: DropdownMenu
    ) {
        binding.dropdownMenuItemTitleText.text = data.title
        binding.dropdownMenuItemInfoText.text = data.getValue()
    }

    private fun showDialog(data: DropdownMenu, after: () -> Unit) {
        AlertDialog.Builder(activity).apply {
            setItems(data.values.toTypedArray()) { _, which ->
                data.onSelected(which, data.values[which])
                after()
            }
        }.show()
    }
}