/**
 * 数量规范化工具函数
 * <p>
 * 商品详情页（ProductDetail.vue）和购物车页（Cart.vue）的数量输入框共用，
 * 避免两处重复实现、将来改规则（如单次购买上限）只改这里一个地方。
 * <p>
 * 背景（code-review 二轮修复）：
 * - 后端 Cart 实体 quantity 是 Integer，前端数量输入允许输入小数/空值/超库存数字
 * - 若不处理，非法值可能传给后端导致 500（Integer.valueOf 抛 NumberFormatException）
 * - 这里统一把用户输入规范化为"合法整数"：取整 → 至少 1
 * <p>
 * ⚠️ 刻意不在本工具里钳制到库存：
 *    "数量超过库存"属于业务校验，由调用方（商品详情页 / 购物车页）判断并提示
 *    "库存不足"，而不是静默钳制成最大库存数（用户明确要求：超库存应提示而非直接最大库存数）。
 *    调用方拿到规范化后的数量后，自行与 product.stock / item.stock 比较。
 *
 * @param {number|string|null} val 用户输入的数量（可能为 null / NaN / 小数 / 负数）
 * @returns {number} 合法整数数量（至少 1；超库存不在此处理，由调用方提示）
 */
export function normalizeQuantity(val) {
  // 取整：el-input-number 的 :precision="0" 会把小数四舍五入为整数（如 2.5 → 3），
  // 这里再取整一次做防御性兜底，保证传给后端的 quantity 永远是整数
  let qty = Math.floor(Number(val))

  // 非法值（0 / 负数 / NaN / 空值）→ 归为 1
  if (!qty || qty < 1) {
    qty = 1
  }

  return qty
}
