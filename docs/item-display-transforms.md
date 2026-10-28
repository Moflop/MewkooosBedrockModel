# 显示变换解析

模型加载时会读取基岩版几何文件的 `item_display_transforms`，并在 v1 `BedrockModel`、v2 `TreeBedrockModel` 和 `BakedBedrockModel` 上通过 `getItemDisplayTransforms()` 暴露。该 API 保存显示参数，调用方负责选择视角、处理默认值并应用渲染变换。

## JSON 位置

`item_display_transforms` 与 `description`、`bones` 同级，位于 `minecraft:geometry` 数组中的几何对象内：

```json
{
  "format_version": "1.21.0",
  "minecraft:geometry": [
    {
      "description": {
        "identifier": "geometry.display_example",
        "texture_width": 16,
        "texture_height": 16,
        "visible_bounds_width": 1,
        "visible_bounds_height": 1,
        "visible_bounds_offset": [0, 0.5, 0]
      },
      "bones": [
        {
          "name": "root",
          "cubes": [{"origin": [-1, 0, -1], "size": [2, 2, 2], "uv": [0, 0]}]
        }
      ],
      "item_display_transforms": {
        "gui": {
          "rotation": [30, 45, 0],
          "translation": [0, 1, 0],
          "scale": [0.625, 0.625, 0.625],
          "rotation_pivot": [0, 0, 0],
          "scale_pivot": [0, 0, 0],
          "fit_to_frame": false
        }
      }
    }
  ]
}
```

## 读取参数

以已加载的 `TreeBedrockModel model` 为例，另外两种模型使用相同的入口：

```java
import mods.mewkooo.bedrockmodel.v1.common.resource.pojo.ItemDisplayTransform;
import mods.mewkooo.bedrockmodel.v1.common.resource.pojo.ItemDisplayTransforms;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

ItemDisplayTransforms display = model.getItemDisplayTransforms();
ItemDisplayTransform gui = display.getTransform(ItemDisplayContext.GUI);
// 等价写法：display.getTransform("gui")

if (gui != null) {
    Vector3f rotation = gui.getRotation();
    Vector3f translation = gui.getTranslation();
    Vector3f scale = gui.getScale();
    // 上述三个值可能为 null，由调用方采用对应视角的默认值。
    Vector3f rotationPivot = gui.getRotationPivot();
    Vector3f scalePivot = gui.getScalePivot();
    boolean fitToFrame = gui.isFitToFrame();
}
```

- 平移保留原始模型像素数值，旋转使用角度，缩放使用倍率；两个 pivot 也保留 JSON 原值。解析不会换算单位、翻转坐标或自动操作 `PoseStack`。
- 未设置的 `rotation`、`translation`、`scale` 返回 `null`，因为不同视角的基础默认值不同。两个 pivot 缺省为零向量；`fit_to_frame` 缺省为 `true`，用于 GUI 自动适配语义，库本身不会执行适配。
- 所有向量 getter 返回防御副本，修改返回值不会改变模型中的参数。
- 未设置的视角返回 `null`，左右手之间不自动继承。模型没有显示变换时，集合为空。
- `getTransforms()` 返回不可变的 `Map<String, ItemDisplayTransform>`。字符串查询可读取 `embedded`、`shelf` 及未来扩展的视角名，不受 Java `ItemDisplayContext` 枚举限制。

格式说明见 [官方 Geometry 1.21.0 参考](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/visualreference/geometry.v1.21.0) 和 [官方 Item Display Transforms 教程](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/blockreference/examples/itemdisplaytransforms)。
