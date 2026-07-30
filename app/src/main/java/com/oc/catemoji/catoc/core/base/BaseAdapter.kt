package com.oc.catemoji.catoc.core.base

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

abstract class BaseAdapter<T, VB : ViewBinding>(private val bindingInflater: (LayoutInflater, ViewGroup, Boolean) -> VB) :
    RecyclerView.Adapter<BaseAdapter<T, VB>.BaseViewHolder>() {

    val items = ArrayList<T>()

    inner class BaseViewHolder(val binding: VB) : RecyclerView.ViewHolder(binding.root) {
        fun bindItem(item: T, position: Int) {
            onBind(binding, item, position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val binding = bindingInflater(LayoutInflater.from(parent.context), parent, false)
        return BaseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        holder.bindItem(items[position], position)
    }

    override fun onBindViewHolder(
        holder: BaseViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            onBindPayload(holder.binding, items[position], position, payloads)
        }
    }

    override fun getItemCount() = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(list: List<T>, isNotChang: Boolean= false) {
        items.clear()
        items.addAll(list)
        if(!isNotChang)
        notifyDataSetChanged()
    }

    protected abstract fun onBind(binding: VB, item: T, position: Int)

    @Suppress("UNCHECKED_CAST")
    protected fun findBinding(
        recyclerView: RecyclerView,
        position: Int
    ): VB? {
        return (recyclerView.findViewHolderForAdapterPosition(position)
            as? BaseAdapter<T, VB>.BaseViewHolder)
            ?.binding
    }

    protected open fun onBindPayload(
        binding: VB,
        item: T,
        position: Int,
        payloads: List<Any>
    ) {
        onBind(binding, item, position)
    }
}
