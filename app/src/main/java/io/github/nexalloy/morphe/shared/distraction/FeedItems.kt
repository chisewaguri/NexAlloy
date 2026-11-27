package io.github.nexalloy.morphe.shared.distraction

internal inline fun <T> List<T>.withoutItems(remove: (T) -> Boolean): List<T> {
    var filtered: ArrayList<T>? = null
    var index = 0
    for (item in this) {
        if (remove(item)) {
            if (filtered == null) filtered = ArrayList(subList(0, index))
        } else {
            filtered?.add(item)
        }
        index++
    }
    return filtered ?: this
}
