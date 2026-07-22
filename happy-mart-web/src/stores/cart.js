import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '@/utils/request'

export const useCartStore = defineStore('cart', () => {
  const items = ref([])

  const totalCount = computed(() => items.value.reduce((sum, item) => sum + item.quantity, 0))
  const totalAmount = computed(() => items.value.reduce((sum, item) => sum + item.price * item.quantity, 0))

  async function fetchCart() {
    const res = await request.get('/cart/list')
    items.value = res.data || []
  }

  async function addItem(productId, quantity = 1) {
    const res = await request.post('/cart/add', { productId, quantity })
    await fetchCart()
    return res
  }

  async function updateQuantity(productId, quantity) {
    const res = await request.put('/cart/update', { productId, quantity })
    await fetchCart()
    return res
  }

  async function removeItem(productId) {
    const res = await request.delete('/cart/remove', { data: { productId } })
    await fetchCart()
    return res
  }

  async function clearCart() {
    const res = await request.delete('/cart/clear')
    items.value = []
    return res
  }

  return { items, totalCount, totalAmount, fetchCart, addItem, updateQuantity, removeItem, clearCart }
})
