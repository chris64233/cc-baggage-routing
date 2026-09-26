# cc-baggage-routing

联程行李、航段和交接事件管理服务：支持按顺序航段的行李路由、旅客改签后安全重建后续路线、误装异常处理链，以及外部扫描事件与改签业务号的幂等处理。

## 开发环境

- JDK 21
- Maven Wrapper 3.9.9
- Spring Boot 4.1.1

迁移项目沿用现有 Spring Boot 版本，其他项目使用上述版本。

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run

## 领域模型

- **Baggage（行李）**：唯一标签号 `tag`、旅客行程（`passengerItinerary`/`passengerName`）和最终目的地。
- **RouteGeneration（路线世代）**：每次建行李（第 1 代）、确认改签或误装接续都会产生一个世代；旧世代永久保留。
- **RouteSegment（计划航段）**：同一世代内按 `segmentNo` 顺序排列，相邻航段必须首尾衔接。
- **BaggageEvent（行李事件）**：装载（LOAD）、卸载（UNLOAD）、交接（HANDOVER）、异常卸载（EXCEPTION_UNLOAD），只追加、不可修改、不可删除。
- **ExternalEventRecord / RebookOrder（幂等记录）**：外部事件号、改签业务号的首次请求指纹与首次结果。
- **ExceptionCase（异常处理记录）**：误装异常卸载与接续计划，通过 `previousCaseNo` 串成异常处理链。

## 主要业务规则

1. **唯一标签与顺序航段**：行李标签全局唯一；建行李时提供旅客行程和按顺序排列的航段，相邻航段必须首尾衔接，行程最终目的地取航段链终点。
2. **位置互斥（地点 XOR 航段）**：行李任一时刻只能处于一个明确地点（`AT_LOCATION`）或一个运输航段（`ON_SEGMENT`），二者互斥；装载、卸载是仅有的两类位置迁移，交接只变更责任方、不改变物理位置。
3. **事件不可变**：所有装载、卸载、交接、异常卸载均以 append-only 事件留痕（序号单调递增），历史事件和已完成航段永远不能被改签或异常处理改写。
4. **改签只针对未装载的后续航段**：旅客改签时行李必须在地面（已完成航段末端）；系统一次性把当前世代所有 `PLANNED` 航段置为 `SUPERSEDED`、旧世代置为 `SUPERSEDED`，并在同一事务中创建新世代，新路线必须从行李当前位置起始、终点仍为行程目的地。已完成航段与既有交接记录不变。
5. **改签业务号幂等**：`orderNo` 全局唯一。重复提交同一业务号返回首次结果（`replayed=true`），不重复创建世代；同一业务号但路线内容变化返回 409 冲突。
6. **并发装载 vs 改签的一致性（XOR）**：所有写操作先对行李行加悲观写锁（`SELECT … FOR UPDATE`），同一行李的装载与改签由数据库串行化，只能形成“原航段装载”或“新路线”其中一种结果，行李不会同时出现在两条路线上；竞争失败方收到 409/422，可安全重试。
7. **外部扫描事件幂等**：`externalEventId` 全局唯一。重复扫描同一事件返回首次结果且不重复产生事件；同一事件号内容变化（如航班号不同）返回 409 冲突。
8. **误装不得直接覆盖位置**：扫描航班与计划航段不符时拒绝装载；在错误地点不允许普通卸载。误装必须调用异常登记接口：在同一事务内追加 `EXCEPTION_UNLOAD` 事件（行李合法地从误装航段迁移到发现地点）、将误装航段置为 `ABORTED`、同世代剩余未执行航段置为 `CANCELLED`，并生成新的接续世代（`continuation`）。
9. **异常处理链**：每次误装生成异常编号（`<标签>-EX-NNN`），多次误装通过 `previousCaseNo` 串链，可完整追溯每次异常卸载及其接续计划。
10. **计划航段防误装校验**：装载扫描必须携带实际航班号且与当前第一个未执行航段一致，装载地点必须与航段起点、行李当前位置一致。

## HTTP 接口

基础路径：`/api/baggage`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/baggage` | 建行李（标签、旅客、顺序航段） |
| GET | `/api/baggage/{tag}` | 行李当前状态（地点/航段二选一） |
| GET | `/api/baggage/{tag}/route` | 计划路线（全部世代及航段状态） |
| GET | `/api/baggage/{tag}/events` | 实际事件（不可变装载/卸载/交接/异常流水） |
| GET | `/api/baggage/{tag}/exceptions` | 异常处理链 |
| POST | `/api/baggage/{tag}/scan` | 外部扫描：`LOAD` / `UNLOAD` / `HANDOVER`（`externalEventId` 幂等） |
| POST | `/api/baggage/{tag}/rebook` | 确认改签（`orderNo` 幂等，原子取消旧尾段并建新路线） |
| POST | `/api/baggage/{tag}/exceptions` | 误装登记（异常卸载 + 接续计划） |

错误码：`400` 参数校验失败；`404` 行李不存在；`409` 幂等内容变化/标签重复/并发竞争；`422` 业务规则冲突（在途改签、航段不衔接、扫描航班不符、误装校验失败等）。

### 请求示例

建行李：

```json
{
  "tag": "BAG001",
  "passengerItinerary": "PNR123",
  "passengerName": "张三",
  "segments": [
    {"transportType": "FLIGHT", "carrier": "CA", "flightNumber": "CA1501", "origin": "PEK", "destination": "PVG"},
    {"transportType": "FLIGHT", "carrier": "CA", "flightNumber": "CA1893", "origin": "PVG", "destination": "SZX"}
  ]
}
```

确认改签（完成 PEK→PVG 后，在 PVG 替换后续航段）：

```json
{
  "orderNo": "RB20260927001",
  "reason": "旅客改签中转",
  "segments": [
    {"transportType": "FLIGHT", "carrier": "MU", "flightNumber": "MU5101", "origin": "PVG", "destination": "CAN"},
    {"transportType": "FLIGHT", "carrier": "MU", "flightNumber": "MU5331", "origin": "CAN", "destination": "SZX"}
  ]
}
```

误装登记（行李实际被错装到 CA8888，在 HGH 被发现）：

```json
{
  "actualFlightNumber": "CA8888",
  "foundAtLocation": "HGH",
  "reason": "实际被装上CA8888，在杭州发现",
  "continuationSegments": [
    {"transportType": "FLIGHT", "carrier": "MU", "flightNumber": "MU5201", "origin": "HGH", "destination": "SZX"}
  ]
}
```

## 测试覆盖

- `BaggageLifecycleTest`：交接/装载/卸载完整流程、改签后旧航段状态与历史事件保留、航段不衔接校验。
- `IdempotencyTest`：外部事件与改签业务号重放返回首次结果、内容变化冲突、在途改签拒绝后业务号可复用、起点/终点校验。
- `ExceptionHandlingTest`：误装异常卸载 + 接续世代 + 多次误装异常链、错误地点禁止普通卸载、地面/航班一致时拒绝异常登记、异常外部事件幂等。
- `ConcurrentLoadRebookTest`：30 轮并发装载 vs 改签竞争，断言恰好一方成功（XOR）且行李不会同时出现在两条路线；同一外部事件 8 线程并发仅处理一次。
- `BaggageControllerTest`：HTTP 全流程与 400/404/409/422 状态码。
