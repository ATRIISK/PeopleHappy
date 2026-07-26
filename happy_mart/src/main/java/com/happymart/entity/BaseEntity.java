package com.happymart.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;       // 字段填充策略 → 告诉 MyBatis-Plus 什么时候自动填这个字段
import com.baomidou.mybatisplus.annotation.TableField;      // 字段注解 → 标记实体类中的字段对应数据库的哪个列
import com.baomidou.mybatisplus.annotation.TableLogic;       // 逻辑删除注解 → 标记这个字段是"逻辑删除"标志位
import lombok.Data;                                          // @Data → 自动生成 getter/setter/toString/equals/hashCode

import java.io.Serializable;                                 // Serializable → 序列化接口，让对象可以在网络传输或存到 Redis
import java.time.LocalDateTime;                              // LocalDateTime → Java 8 的时间类型，对应 MySQL 的 datetime

/**
 * 实体类的公共基类（父类）
 * <p>
 * 这个类是所有实体类（User、Product、Category 等）的"爸爸"。
 * 所有实体类都继承它，这样每个表就自动有了三个公共字段：
 * <ul>
 *   <li>create_time → 创建时间</li>
 *   <li>update_time → 更新时间</li>
 *   <li>is_deleted  → 逻辑删除标志（0=没删，1=已删）</li>
 * </ul>
 * <p>
 * 为什么要搞一个基类？
 * 因为几乎每个表都需要记录"什么时候创建的、什么时候修改的、有没有被删掉"。
 * 如果每个实体类都自己写一遍这三个字段，太累了。
 * 写在基类里，所有实体类继承一下就都有了。
 * <p>
 * 继承关系：
 * <pre>
 *   BaseEntity（基类：公共字段）
 *       ↑
 *   User / Product / Category / Cart ...（子类：自己的字段）
 * </pre>
 * 比如 User 类继承 BaseEntity 后，User 就有以下字段：
 * id（自己的）+ username（自己的）+ password（自己的）
 * + createTime（从爸爸来的）+ updateTime（从爸爸来的）+ deleted（从爸爸来的）
 * <p>
 * abstract 关键字（抽象类）是什么意思？
 * 意思是：这个类不能直接 new（不能 new BaseEntity()），只能被继承。
 * 因为 BaseEntity 本身不代表任何一张表，它只是提供公共字段。
 */
@Data                                                       // Lombok → 自动生成 getter/setter/toString/equals/hashCode
public abstract class BaseEntity implements Serializable {  // 抽象类 + 实现序列化接口

    // ==================== 这三个字段是所有表都有的 ====================

    /**
     * 创建时间
     * <p>
     * 对应数据库的 create_time 字段（datetime 类型）。
     * <p>
     * 注解 {@code @TableField(fill = FieldFill.INSERT)} 是什么意思？
     * 这是 MyBatis-Plus 的自动填充功能。
     * fill = FieldFill.INSERT 的意思是：插入数据时自动填充。
     * 也就是说，当你 insert 一条记录时，MyBatis-Plus 会自动把当前时间设到这个字段里，
     * 你不用手动写 setCreateTime(LocalDateTime.now())。
     * <p>
     * 自动填充的逻辑在哪里？
     * 在 MyBatisPlusConfig.java 的 MetaObjectHandler 里：
     * strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now())
     * 这句话的意思是：插入时，把 createTime 设成当前时间
     */
    @TableField(fill = FieldFill.INSERT)                    // 插入时自动填充
    private LocalDateTime createTime;

    /**
     * 更新时间
     * <p>
     * 对应数据库的 update_time 字段（datetime 类型）。
     * <p>
     * 注解 {@code @TableField(fill = FieldFill.INSERT_UPDATE)} 是什么意思？
     * 插入和更新时都会自动填充。
     * 也就是说：
     * <ul>
     *   <li>插入一条数据时 → 自动设成当前时间（和 createTime 一样）</li>
     *   <li>更新这条数据时 → 自动更新为当前时间（记录"什么时候修改的"）</li>
     * </ul>
     * 这样你修改数据时就不用手动写 setUpdateTime(LocalDateTime.now()) 了。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)             // 插入和更新时都会自动填充
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标志
     * <p>
     * 对应数据库的 is_deleted 字段（TINYINT 类型，0 或 1）。
     * <p>
     * <b>什么是逻辑删除？</b>
     * <br>
     * 正常删数据是 DELETE FROM user WHERE id = 1（物理删除，真删掉了）。
     * 逻辑删除是 UPDATE user SET is_deleted = 1 WHERE id = 1（假装删了，其实还在数据库里）。
     * <p>
     * <b>好处：</b>
     * <ol>
     *   <li>数据不会真的丢失，哪天想恢复还能恢复</li>
     *   <li>历史数据还在，可以统计分析</li>
     *   <li>用户误操作删了账号，管理员能恢复</li>
     * </ol>
     * <p>
     * <b>{@code @TableLogic} 注解做了什么？</b>
     * <br>
     * 加了 @TableLogic 后，MyBatis-Plus 会自动做两件事：
     * <ol>
     *   <li>查询时自动加 WHERE is_deleted = 0（只查没被删的）</li>
     *   <li>调用 deleteById() 时，不会执行 DELETE，而是执行 UPDATE SET is_deleted = 1</li>
     * </ol>
     * <p>
     * <b>{@code @TableField("is_deleted")} 是什么意思？</b>
     * <br>
     * 因为 Java 字段名叫 deleted，但数据库列名叫 is_deleted，
     * 所以要用这个注解告诉 MyBatis-Plus：Java 的 deleted 对应数据库的 is_deleted 列。
     * 如果数据库列名也是 deleted，就不用加这个了（但数据库里是 is_deleted）。
     * <p>
     * <b>取值：</b>
     * <ul>
     *   <li>0 = 未删除（正常数据）</li>
     *   <li>1 = 已删除（逻辑上被删了）</li>
     * </ul>
     * <p>
     * 这个配置在 application.yml 里：
     * <pre>
     *   logic-delete-field: deleted
     *   logic-delete-value: 1            // 1 表示已删除
     *   logic-not-delete-value: 0        // 0 表示未删除
     * </pre>
     */
    @TableLogic                                                  // 逻辑删除注解
    @TableField("is_deleted")                                    // 数据库列名是 is_deleted，不是 deleted
    private Integer deleted;

    /* ⚠️ 注意！
     * 你没有在 User.java / Product.java / Category.java 里看到这三个字段
     * 因为它们定义在基类 BaseEntity 里，子类自动继承
     * 就像你有一个爸爸，爸爸有房子（字段），你也自然有房子
     */

    // ==================== 序列化 ====================

    /* 为什么实现 Serializable 接口？
     *
     * Serializable 是 Java 的序列化接口。
     * 序列化 = 把 Java 对象转成字节流（可以存到文件/数据库/Redis，或者网络传输）。
     *
     * 比如：
     *   - 把 User 对象存到 Redis 缓存 → 需要序列化
     *   - 在网络上传输对象 → 需要序列化
     *
     * serialVersionUID 呢？
     * 没写也没事，Java 会自动生成一个。但显式写一个更好，防止不同版本的类反序列化失败。
     * 不过为了简单，这里没写也能正常运行。
     */
}