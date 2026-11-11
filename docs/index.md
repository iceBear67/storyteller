---
layout: home

hero:
  name: Storyteller
  text: 用数据包脚本驱动对话
  tagline: 为 Minecraft RPG 地图准备的对话系统
  actions:
    - theme: brand
      text: 快速开始
      link: /tutorial/setup
    - theme: alt
      text: 脚本文法
      link: /reference/syntax

features:
  - title: 脚本驱动
    details: 对话写在 data/<命名空间>/dialog/ 下的文本文件里，修改后重载数据包即可生效。
  - title: 条件与选择
    details: 每个动作都可以附带条件，点击对白做出选择，玩家的选择写进状态，改变后续剧情。
  - title: 进度存档
    details: 玩家的对话进度保存在世界存档里，下线重进从上次的位置继续。
  - title: 地图集成
    details: 三种触发实体把区域、交互与方块事件接进对话，实体选择器把状态带进任意原版命令。
---

## 这是什么

Storyteller 是一个 Fabric 模组，用来在地图里编排对话。它从数据包读取脚本，按游戏刻依次显示对白、执行命令，并把玩家进度写入世界存档。
