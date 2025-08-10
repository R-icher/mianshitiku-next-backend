# 面试刷题平台

# 库表设计

在设计用户表的时候还设计了如下两条字段：

```sql
    unionId      varchar(256)                           null comment '微信开放平台id',
    mpOpenId     varchar(256)                           null comment '公众号openId',
```

`unionI⁠d`、`mpOpenId` 是为了实现公众号登录的，每个微信用﻿户在同**一家公司**（主体）的 `unio⁢nId` 是唯一的，在同**一个公众号**的‍ `mpOpenId` 是唯一的。

（区别：`mpOpenId` 的范围比 `unionId` 的范围更小）



- ***扩展设计：***

给 `userRole` 字段新增枚举值 `vip`，表示会员用户，可根据该值判断用户权限。

```SQL
  /*在一战表内设计如下字段*/
  
  `userRole`     ENUM('user', 'admin', 'ban', 'vip')
                  NOT NULL DEFAULT 'user'
                  COMMENT '用户角色：user/admin/ban/vip',
  `vipExpireTime` DATETIME    NULL COMMENT '会员过期时间',
  `vipCode`       VARCHAR(128) NULL COMMENT '会员兑换码',
  `vipNumber`     BIGINT       NULL COMMENT '会员编号',
```



- ***在题目表的设计中添加 vip 题目：***

```SQL
needVip  tinyint  default 0  not null comment '仅会员可见（1 表示仅会员可见）'
```





## 索引设计规范

当发现某个字段是被用户频繁搜索的，并且区分度较高，就给该字段添加索引：（例如设计题目表的时候，题目的标题以及由于每个用户都能去创建题目，后期为了便于管理，需要知道该用户创建了哪些题目，因此给用户也设计索引）

```sql
    title      varchar(256)                       null comment '标题',
  	userId     bigint                             not null comment '创建用户 id',
    
    index idx_title (title),
    index idx_userId (userId)
```





## 题库表和题目表之间的关系表

```sql
-- 题库题目表（硬删除）
create table if not exists question_bank_question
(
    id             bigint auto_increment comment 'id' primary key,
    questionBankId bigint                             not null comment '题库 id',
    questionId     bigint                             not null comment '题目 id',
    userId         bigint                             not null comment '创建用户 id',
    createTime     datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime     datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    UNIQUE (questionBankId, questionId)
) comment '题库题目' collate = utf8mb4_unicode_ci;
```

`UNIQUE (questionBankId, questionId)` 这里设置了**联合唯一索引**，防止题目被重复添加到同一个题库中。

将题库 id ：`questionBankId` 放在前面的原因是遵循索引的最左匹配原则，用户一定是先进入题库，再查询到题库下的题目







# 根据题库分页获取题目列表

由于是两张表，因此要先去关系表中把对应的题库 ID 取到，然后通过 `MyBatisPlus` 的拼接 `sql` 语句，将查询题库 Id 和题目 Id拼接在一起。

```Java
// 获取题库 id
Long questionBankId = questionQueryRequest.getQuestionBankId();
```

------

- ***将题库 Id 和 题目 Id 做 `sql` 拼接：***

```Java
 // 根据题库 id 取比对连接表，查找到对应的题目id
LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                    .select(QuestionBankQuestion :: getQuestionId)
                    .eq(QuestionBankQuestion :: getQuestionBankId, questionBankId);

// 将所有查找出来的问题 Id 放到一个集合中
List<QuestionBankQuestion> questionList = questionBankQuestionService.list(lambdaQueryWrapper);
```

------

- ***此时取出的 `questionList` 还是一个 `QuestionBankQuestion` 对象，我们需要的是这个对象其中的题目 Id 对象：***

```java
            if(CollUtil.isNotEmpty(questionList)){
                // 取出题目 id 集合
                Set<Long> questionIdSet = questionList.stream()
                        .map(QuestionBankQuestion::getQuestionId)
                        .collect(Collectors.toSet());

                // 拼接 sql 查询
                queryWrapper.in("id", questionIdSet);
            }
```

------

- ***最后根据刚才查询的 sql 语句到数据库内进行查找：***

```Java
// 查询数据库
Page<Question> questionPage = this.page(new Page<>(current, size), queryWrapper);
return questionPage;
```







# 获取题库详情

首先要知道获取题库详情需要哪些参数，将这些参数封装到 `QuestionBankQueryRequest`类中传给获取题库详情方法：

```Java
@EqualsAndHashCode(callSuper = true)
@Data
public class QuestionBankQueryRequest extends PageRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * id
     */
    private Long notId;

    /**
     * 搜索词
     */
    private String searchText;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String description;

    /**
     * 图片
     */
    private String picture;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 是否要关联查询题库列表
     */
    private boolean needQueryQuestionList;

    private static final long serialVersionUID = 1L;
}
```

------

- ***然后根据这个对象获取到对应的题库id，再根据题库id获取到对应的题库对象：***

```Java
        Long id = questionBankQueryRequest.getId();

        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 根据题库 id 获取到题库对象
        QuestionBank questionBank = questionBankService.getById(id);
        ThrowUtils.throwIf(questionBank == null, ErrorCode.NOT_FOUND_ERROR);
```

------

- ***将题库对象封装给 VO 类，返回给前端展示***

```Java
        // 将题库对象封装给 VO 类，返回给前端展示
        QuestionBankVO questionBankVO  = questionBankService.getQuestionBankVO(questionBank, request);
```

------

- ***判断返回题库对象的时候是否要将题库下的题目列表也一起返回，因此在上面创建对象的时候多了` private boolean needQueryQuestionList;`***

```java
        // 是否要关联查询题库下的题目列表
        boolean needQueryQuestionList = questionBankQueryRequest.isNeedQueryQuestionList();
        if(needQueryQuestionList){
            QuestionQueryRequest questionQueryRequest = new QuestionQueryRequest();
            questionQueryRequest.setQuestionBankId(id);  // 根据题库 id 查找到对应的题目
            Page<Question> questionPage = questionService.listQuestionByPage(questionQueryRequest);
            questionBankVO.setQuestionPage(questionPage);
        }

        // 获取封装类
        return ResultUtils.success(questionBankVO);
```







# 用户刷题记录日历（扩展）

1. 用户每日首次浏览题目，算作是签到，会记录在系统中。
2. 用户可以在前端以图表的形式查看自己在 **某个年份** 的刷题签到记录（每天是否有签到）。



## Redis键的设计规范

- 明确性：键名称应明确表示数据的含义和结构。例如，通过使用 `signins` 可以清楚地知道这个键与用户的签到记录有关。
- 层次结构：使用冒号 `:` 分隔不同的部分，可以使键结构化，便于管理和查询。
- 唯一性：确保键的唯一性，避免不同数据使用相同的键前缀。
- 一致性：在整个系统中保持键设计的一致性，使得管理和维护变得更加简单。
- 长度：避免过长的键名称，以防影响性能和存储效率。



例如在设计用户刷题记录日志的时候可以设计为：`user:signins:{userId}`

- `user` 是业务领域前缀
- `signins` 是具体操作或功能
- `{userId}` 表示每个用户，是动态值





## (面试)BitMap

Bitmap 位图，是一种使用位（bit）来表示数据的 **紧凑** 数据结构。每个位可以存储两个值：0 或 1，常用于表示某种状态或标志。

在这里，⁠我们签到的状态可以用 0 和 1 表﻿示，0 代表未签到⁢，1 代表签到。**（可以大大节约存储空间，每个是否签到的信息只占一位）**

```
0101 表示第 1 天和第 3 天已签到
1010 表示第 2 天和第 4 天已签到
```



在本项目中，Redis Key 可以设计成 `user:signins:{年份}:{userId}` 。

**刷题签到逻辑：**

1. 当已登录用户进入题目详情页时，调用接口，触发签到。
2. 获取当前日期是一年中的第几天，作为偏移量在 Bitmap 设置为 true。

**查询刷题签到记录：**

1. 通过 userId 和当前年份从 Redis 中获取对应的 Bitmap
2. 获取当前年份的总天数
3. 循环天数拼接日期，根据日期去 Bitmap 中判断是否有签到记录，并记录到数组中
4. 最后，将拼接好的、一年的签到记录返回给前端





## (面试)Redisson的使用

- ***首先需要加载 `Redisson` 的配置类：***

```Java
/**
 * Redisson 的配置
 */
@Configuration
@ConfigurationProperties(prefix = "spring.redis")  // 代表去application.yml中找spring下的redis标签
@Data
public class RedissonConfig {

    private String host;
    private Integer port;
    private Integer database;

    @Bean
    public RedissonClient redissonClient(){  // 创建一个 RedissonClient 对象
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database);
        return Redisson.create(config);
    }
}
```



- ***由于我们需要将用户的签到记录保存到 Redis 中的 Bitmap 类型中，因此需要指定 key 的命名规范：***

```Java
public interface RedisConstant {

    /**
     * 用户签到记录的 Redis key 前缀
     */
    String USER_SIGN_IN_REDIS_KEY_PREFIX = "user:signins";

    /**
     * 获取用户签到记录的 Redis Key
     * @param year 年份
     * @param userId 用户id
     * @return 拼接好的 Redis Key
     */
    static String getUserSignInRedisKey(int year, long userId){
        return String.format("%s:%s:%S", USER_SIGN_IN_REDIS_KEY_PREFIX, year, userId);
    }
}
```



- **用户签到是有关用户的操作，因此来到 `UserServiceImpl` 实现类中，获取用户签到的当前年，以及用户的id做key的拼接**
- **然后需要计算今天的签到日是今年的第几天，放到 Bitmap 中的对应位作为偏移量：**

```Java
    /**
     * 添加用户签到记录
     * @param userId 用户 id
     * @return 当前用户是否已签到成功
     */
    @Override
    public boolean addUserSignIn(long userId) {
        LocalDateTime date = LocalDateTime.now();
        String key = RedisConstant.getUserSignInRedisKey(date.getYear(), userId);
        // 获取 Redis 的 BitMap
        RBitSet signInBitSit = redissonClient.getBitSet(key);
        // 获取当前日期是一年中的第几天，作为偏移量（从 1 开始计数）
        int offset = date.getDayOfYear();
        // 查询当天有没有签到
        if(!signInBitSit.get(offset)){
            // 如果当天未签到，则设置为 false
            signInBitSit.set(offset, true);
        }
        // 当天已签到
        return true;
    }
```







# *获取用户签到日期（BitMap计算优化）

在 Java 中的 `BitSet` 类中，可以使用 `nextSetBit(int fromIndex)` 和 `nextClearBit(int fromIndex)` 方法来获取从指定索引开始的下一个 **已设置（即为 1）** 或 **未设置（即为 0）** 的位。

- `nextSetBit(int fromIndex)`：从 `fromIndex` 开始（包括 `fromIndex` 本身）寻找下一个被设置为 1 的位。如果找到了，返回该位的索引；如果没有找到，返回 -1。
- `nextClearBit(int fromIndex)`：从 `fromIndex` 开始（包括 `fromIndex` 本身）寻找下一个为 0 的位。如果找到了，返回该位的索引；如果没有找到，返回一个大的整数值。



**例如项目中的签到优化，就可以通过遍历位而不是用 for 循环遍历一年的每一天来实现性能优化：**

```Java
        // 统计签到的日期
        List<Integer> dayList = new ArrayList<>();

        // 从索引 0 开始查找下一个被设为 1 的位, (index 就代表第几天的签到)
        int index = bitSet.nextSetBit(0);
        while(index >= 0){
            dayList.add(index);
            // 查找下一个被设为 1 的位
            index = bitSet.nextSetBit(index + 1);
        }
        return dayList;
```







# (面试)Elasticsearch 搜索

当前用的搜索模式是 `mysql` 的模糊查询，因此如果搜索的字段当中不是连续的匹配标题，就会无法查找出来，而使用 ES 就能够支持分词匹配和多字段搜索。



## 核心

索引（Index）：类似于关系型数据库中的表，索引是数据存储和搜索的 **基本单位**。每个索引可以存储多条文档数据。



- ***分片（Shard⁠）：为了实现横向扩展，ES 将索引拆分成多个分片，每个分片可﻿以分布在不同节点上。     ⁢               ‍***            
- ***副本（Re⁠plica）：分片的复制品，用于提高﻿可用性和容错性。***

<img src="./面试刷题平台.assets/屏幕截图 2025-08-01 120300.png" style="zoom:50%;" />





## ES实现全文检索

1. ES 的分词器会将输入文本拆解成独立的词条(tokens)，方便进行索引和搜索，分词的具体过程包含以下几步：

- 字符过滤：去除特殊字符、HTML 标签或进行其他文本清理。
- 分词：根据指定的分词器（analyzer），将文本按规则拆分成一个个词条。例如，**英文可以按空格拆分，中文使用专门的分词器处理。**
- 词汇过滤：对分词结果进行过滤，如去掉停用词（常见但无意义的词，如 "the"、"is" 等）或进行词形归并（如将动词变为原形）。

------



2. ***倒排索引***

- 每个文档在被索引时，分词器会将文档内容拆解为多个词条。
- 然后，Elasticsearch 为每个词条生成一个倒排索引，记录该词条在哪些文档中出现。



举个例子，假设有两个文档：

- 文档 1：鱼皮是帅锅
- 文档 2：鱼皮是好人

中文分词后，生成的倒排索引大致如下：

| 词条 | 文档 ID |
| ---- | ------- |
| 鱼皮 | 1, 2    |
| 是   | 1, 2    |
| 帅锅 | 1       |
| 好人 | 2       |





## ES的打分规则

打分规则（_Score）是用于衡量每个文档与查询条件的匹配度的评分机制。

搜索结果的默认排序方式是按相关性得分（_score）从高到低。Elasticsearch 使用 **BM25 算法** 来计算每个文档的得分，它是基于**词频**、**反向文档频率**、**文档长度**等因素来评估文档和查询的相关性。





## ES查询语法

1. DSL 查询

```json
{
  "query": {
    "match": {
      "message": "Elasticsearch 是强大的"
    }
  }
}
```

这个查询会对 `message` 字段进行分词，并查找包含 "Elasticsearch" 和 "强大" 词条的文档。



2. SQL 查询

```sql
SELECT name, age FROM users WHERE age > 30 ORDER BY age DESC
```

这个查询会返回 `users` 索引中 `age` 大于 30 的所有用户，并按年龄降序排序.





## ES查询条件

| **查询条件**   | **介绍**                                                     | **示例**                                                     | **用途**                                           |
| -------------- | ------------------------------------------------------------ | ------------------------------------------------------------ | -------------------------------------------------- |
| **`match`**    | 用于全文检索，将查询字符串进行分词并匹配文档中对应的字段。   | `{ "match": { "content": "鱼皮是帅小伙" } }`                 | 适用于全文检索，分词后匹配文档内容。               |
| **`term`**     | 精确匹配查询，不进行分词。通常用于结构化数据的精确匹配，如数字、日期、关键词等。 | `{ "term": { "status": "active" } }`                         | 适用于字段的精确匹配，如状态、ID、布尔值等。       |
| `terms`        | 匹配多个值中的任意一个，相当于多个 `term` 查询的组合。       | `{ "terms": { "status": ["active", "pending"] } }`           | 适用于多值匹配的场景。                             |
| `range`        | 范围查询，常用于数字、日期字段，支持大于、小于、区间等查询。 | `{ "range": { "age": { "gte": 18, "lte": 30 } } }`           | 适用于数值或日期的范围查询。                       |
| **`bool`**     | 组合查询，通过 `must`、`should`、`must_not` 等组合多个查询条件。 | `{ "bool": { "must": [ { "term": { "status": "active" } }, { "range": { "age": { "gte": 18 } } } ] } }` | 适用于复杂的多条件查询，可以灵活组合。             |
| `**wildcard**` | 通配符查询，支持 `*` 和 `?`，前者匹配任意字符，后者匹配单个字符。 | `{ "wildcard": { "name": "鱼*" } }`                          | 适用于部分匹配的查询，如模糊搜索。                 |
| `prefix`       | 前缀查询，匹配以指定前缀开头的字段内容。                     | `{ "prefix": { "name": "鱼" } }`                             | 适用于查找以指定字符串开头的内容。                 |
| **`fuzzy`**    | 模糊查询，允许指定程度的拼写错误或字符替换。                 | `{ "fuzzy": { "name": "yupi~2" } }`                          | 适用于处理拼写错误或不完全匹配的查询。             |
| `exists`       | 查询某字段是否存在。                                         | `{ "exists": { "field": "name" } }`                          | 适用于查找字段存在或缺失的文档。                   |
| `match_phrase` | 短语匹配查询，要求查询的词语按顺序完全匹配。                 | `{ "match_phrase": { "content": "鱼皮 帅小伙" } }`           | 适用于严格的短语匹配，词语顺序和距离都严格控制。   |
| `match_all`    | 匹配所有文档。                                               | `{ "match_all": {} }`                                        | 适用于查询所有文档，通常与分页配合使用。           |
| `ids`          | 基于文档 ID 查询，支持查询特定 ID 的文档。                   | `{ "ids": { "values": ["1", "2", "3"] } }`                   | 适用于根据文档 ID 查找特定文档。                   |
| `geo_distance` | 地理位置查询，基于地理坐标和指定距离查询。                   | `{ "geo_distance": { "distance": "12km", "location": { "lat": 40.73, "lon": -74.1 } } }` | 适用于根据距离计算查找地理位置附近的文档。         |
| `aggregations` | 聚合查询，用于统计、计算和分组查询，类似 SQL 中的 `GROUP BY`。 | `{ "aggs": { "age_stats": { "stats": { "field": "age" } } } }` | 适用于统计和分析数据，比如求和、平均值、最大值等。 |

其中的几个关键：

1. 精确匹配 vs. 全文检索：`term` 是精确匹配，不分词；`match` 用于全文检索，会对查询词进行分词。
2. 组合查询：`bool` 查询可以灵活组合多个条件，适用于复杂的查询需求。
3. 模糊查询：`fuzzy` 和 `wildcard` 提供了灵活的模糊匹配方式，适用于拼写错误或不完全匹配的场景。





## (面试)ES数据同步方案

如果做查询搜索功能，使用 ES 来模糊搜索，但是数据是保存在 MySQL 中的，所以需要把 MySQL 中的数据和 ES 中的做同步，保证数据一致。



**（1）定时任务**

比如一分钟一次，找到 MySQL 中过去几分钟内发生改变的数据，然后更新到 ES

应用场景：数据实时性要求不高，适合数据短时间内不同步不会带来重大影响的场景

------

**（2）双写**

写数据的时候，必须也要去写入到ES中

------

**（3）Logstash 数据同步管道**

基于配置文件，减少了手动编码，数据同步逻辑和业务代码解耦，一般要配合 `kafka` 消息队列 + beats 采集器，进行流量削峰，维护成本高

------

**（4）监听 `MySQL Binlog`**

有任何数据更新都能够实时监听到，并且同步到 ES 中。一般不需要自己监听，可以使用现成的技术，比如 Canal

- **实时性强**：能够在 MySQL 数据发生变更的第一时间同步到 ES，做到真正的实时同步。





## (面试)分词器的选择

即在 "analyzer" 标签之后应该填什么：

- `ik_smart`：适用于 **搜索分词**，即在查询时使用，保证性能的同时提供合理的分词精度。
- `ik_max_word`：适用于 **底层索引分词**，确保在建立索引时尽可能多地分词，提高查询时的匹配度和覆盖面。





## (面试)设计ES索引

```json
{
  "mappings": {
    "properties": {
      "title": {
        "type": "text",
        "analyzer": "ik_max_word",
        "search_analyzer": "ik_smart",
        "fields": {
          "keyword": {
            "type": "keyword",
            "ignore_above": 256
          }
        }
      },
      "content": {
        "type": "text",
        "analyzer": "ik_max_word",
        "search_analyzer": "ik_smart"
      },
      "tags": {
        "type": "keyword"
      },
      "answer": {
        "type": "text",
        "analyzer": "ik_max_word",
        "search_analyzer": "ik_smart"
      },
      "userId": {
        "type": "long"
      },
      "editTime": {
        "type": "date",
        "format": "yyyy-MM-dd HH:mm:ss"
      },
      "createTime": {
        "type": "date",
        "format": "yyyy-MM-dd HH:mm:ss"
      },
      "updateTime": {
        "type": "date",
        "format": "yyyy-MM-dd HH:mm:ss"
      },
      "isDelete": {
        "type": "keyword"
      }
    }
  }
}
```

------



1）title、content、answer：

这些字段被定义为 `text` 类型，适合存储较长的、需要全文搜索的内容。由于会有中文内容，所以使用了 IK 中文分词器进行分词处理，以提高查询的灵活性和匹配度。

- `analyzer: ik_max_word`：用于索引时进行最大粒度的分词，生成较多词语，适合在查询时提高召回率。
- `search_analyzer: ik_smart`：用于搜索时进行较智能的分词，生成较少的词语，通常用于提高搜索精度。

------

2）`title.keyword`：为 `title` 字段增加了一个子字段 `keyword`，用于存储未分词的标题，支持精确匹配。它还配置了 `ignore_above: 256`，表示如果 title 字段的长度超过 256 个字符，将不会为 keyword 字段进行索引。因为题目的标题一般不会很长，很少会对过长的标题进行精确匹配，所以用这一设置来避免过长文本导致的性能问题。

------

3）tags：标签通常是预定义的、用于分类或标签筛选的关键字，通常不需要分词。设置为 `keyword` 类型以便支持精确匹配和聚合操作（例如统计某标签的出现频次）。`keyword` 不进行分词，因此适合存储不变的、结构化的数据。

------

4）userId：用来唯一标识用户的数值字段。在 Elasticsearch 中，数值类型（如 `long`）非常适合用于精确查询、排序和范围过滤。与字符串相比，数值类型的查询和存储效率更高，尤其是对于大量用户数据的查询。

------

5）`editTime、createTime、updateTime`：时间字段被定义为 `date` 类型，并指定了格式 `"yyyy-MM-dd HH:mm:ss"`。这样做的好处是 Elasticsearch 可以基于这些字段进行时间范围查询、排序和聚合操作，如按时间过滤或统计某时间段的数据。

------

6）`isDel⁠ete`：使用 keyword 类型，表示是否被删除。 因﻿为 keyword 是为精确⁢匹配设计的，适用于枚举值精确‍查询的场景，性能好且清晰。

为什么不用⁠ `boolean` 类型呢？因为 `MySQ﻿L` 数据库存储的是 ⁢0 和 1，写入 E‍S 时需要转换类型。



- ***注意：推荐在创建索引时添加 alias 标签（表示别名）***

```json
PUT /my_index_v1
{
  "aliases": {
    "my_index": {}
  }
}
```

这样，别名就被设置为了 `my_index`，**在项目中我们将这个索引别名设置为 `question`**





## 将数据库数据保存到ES中

- ***首先我们需要 ES 实体类：`QuestionEsDTO`***

```java
// 指定和 ES 的哪个索引关联
@Document(indexName = "question")
@Data
public class QuestionEsDTO implements Serializable {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * id
     */
    @Id
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 答案
     */
    private String answer;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 创建时间，确保 ES 中的日期格式和 Java 中的日期格式相同
     */
    @Field(type = FieldType.Date, format = {}, pattern = DATE_TIME_PATTERN)
    private Date createTime;

    /**
     * 更新时间，确保 ES 中的日期格式和 Java 中的日期格式相同
     */
    @Field(type = FieldType.Date, format = {}, pattern = DATE_TIME_PATTERN)
    private Date updateTime;

    /**
     * 是否删除
     */
    private Integer isDelete;

    private static final long serialVersionUID = 1L;

    /**
     * 对象转包装类
     *
     * @param question
     * @return
     */
    public static QuestionEsDTO objToDto(Question question) {
        if (question == null) {
            return null;
        }
        QuestionEsDTO questionEsDTO = new QuestionEsDTO();
        BeanUtils.copyProperties(question, questionEsDTO);
        String tagsStr = question.getTags();
        if (StringUtil.isNotBlank(tagsStr)) {
            questionEsDTO.setTags(JSONUtil.toList(tagsStr, String.class));
        }
        return questionEsDTO;
    }

    /**
     * 包装类转对象
     *
     * @param questionEsDTO
     * @return
     */
    public static Question dtoToObj(QuestionEsDTO questionEsDTO) {
        if (questionEsDTO == null) {
            return null;
        }
        Question question = new Question();
        BeanUtils.copyProperties(questionEsDTO, question);
        List<String> tagList = questionEsDTO.getTags();
        if (CollUtil.isNotEmpty(tagList)) {
            question.setTags(JSONUtil.toJsonStr(tagList));
        }
        return question;
    }
}
```



- ***然后需要一个接口（可使用Dao层编码规则）去实现 ES 的核心 CRUD 功能：***

```Java
/**
 * 题目 ES 操作
 */
public interface QuestionEsDao extends ElasticsearchRepository<QuestionEsDTO, Long> {

    List<QuestionEsDTO> findByUserId(Long userId);
}
```



- ***将数据库数据全量同步到 ES：***

```Java
/**
 * 全量同步题目到 es
 */
// todo 取消注释开启任务
@Component
@Slf4j
public class FullSyncQuestionToEs implements CommandLineRunner {

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionEsDao questionEsDao;

    @Override
    public void run(String... args) {
        // 全量获取题目（数据量不大的情况下使用）
        List<Question> questionList = questionService.list();
        if (CollUtil.isEmpty(questionList)) {
            return;
        }
        
        // 转为 ES 实体类
        List<QuestionEsDTO> questionEsDTOList = questionList.stream()
                .map(QuestionEsDTO::objToDto)
                .collect(Collectors.toList());
        
        // 分页批量插入到 ES
        final int pageSize = 500;
        int total = questionEsDTOList.size();
        log.info("FullSyncQuestionToEs start, total {}", total);
        for (int i = 0; i < total; i += pageSize) {
            // 注意同步的数据下标不能超过总数据量
            int end = Math.min(i + pageSize, total);
            log.info("sync from {} to {}", i, end);
            questionEsDao.saveAll(questionEsDTOList.subList(i, end));
        }
        log.info("FullSyncQuestionToEs end, total {}", total);
    }
}
```



- ***还需要一个监听类，每过一分钟查询一次近5分钟内的数据，做增量同步：***

```Java
/**
 * 增量同步题目到 es
 */
// todo 取消注释开启任务
@Component
@Slf4j
public class IncSyncQuestionToEs {

    @Resource
    private QuestionMapper questionMapper;

    @Resource
    private QuestionEsDao questionEsDao;

    /**
     * 每分钟执行一次
     */
    @Scheduled(fixedRate = 60 * 1000)
    public void run() {
        // 查询近 5 分钟内的数据
        long FIVE_MINUTES = 5 * 60 * 1000L;
        Date fiveMinutesAgoDate = new Date(new Date().getTime() - FIVE_MINUTES);
        List<Question> questionList = questionMapper.listQuestionWithDelete(fiveMinutesAgoDate);
        if (CollUtil.isEmpty(questionList)) {
            log.info("no inc question");
            return;
        }

        // 执行全量更新操作
        
        /*   和上面的全量同步逻辑完全一致   */
    }
}
```





### 用ES将查询得到的数据分页返回给前端

据前端传来的各种查询条件，在 Elasticsearch 里检索符合要求的题目，并将结果按分页、排序的方式封装成项目里的 `Page<Question>` 对象返回.

```Java
    @Override
    public Page<Question> searchFromES(QuestionQueryRequest questionQueryRequest) {
        // 获取参数
        Long id = questionQueryRequest.getId();
        Long notId = questionQueryRequest.getNotId();
        String searchText = questionQueryRequest.getSearchText();
        List<String> tags = questionQueryRequest.getTags();
        Long questionBankId = questionQueryRequest.getQuestionBankId();
        Long userId = questionQueryRequest.getUserId();
        // 注意，ES 的起始页为 0
        int current = questionQueryRequest.getCurrent() - 1;
        int pageSize = questionQueryRequest.getPageSize();
        String sortField = questionQueryRequest.getSortField();
        String sortOrder = questionQueryRequest.getSortOrder();

        // BoolQueryBuilder：相当于一个“容器”，能同时承载多种 must、filter、should、mustNot 子句
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        // 先统一过滤掉已删除的数据，filter用于精确匹配
        boolQueryBuilder.filter(QueryBuilders.termQuery("isDelete", 0));

        // termQuery：对字段做精确匹配
        if (id != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("id", id));
        }
        // mustNot：排除某些 id
        if (notId != null) {
            boolQueryBuilder.mustNot(QueryBuilders.termQuery("id", notId));
        }
        if (userId != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("userId", userId));
        }
        if (questionBankId != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("questionBankId", questionBankId));
        }
        // 必须包含所有标签
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                boolQueryBuilder.filter(QueryBuilders.termQuery("tags", tag));
            }
        }

        // 按关键字全文检索
        if (StringUtils.isNotBlank(searchText)) {
            // should 子句：表示“至少符合一个就行”，但不会像 filter 那样完全排除不匹配的文档，而是通过评分衡量相关度。
            boolQueryBuilder.should(QueryBuilders.matchQuery("title", searchText));
            boolQueryBuilder.should(QueryBuilders.matchQuery("content", searchText));
            boolQueryBuilder.should(QueryBuilders.matchQuery("answer", searchText));
            // minimumShouldMatch(1)：至少要命中一个 should 子句，才能被认为匹配。
            boolQueryBuilder.minimumShouldMatch(1);
        }

        // 默认按 Elasticsearch 自带的 _score 排序（相关度）
        SortBuilder<?> sortBuilder = SortBuilders.scoreSort();
        if (StringUtils.isNotBlank(sortField)) {
            sortBuilder = SortBuilders.fieldSort(sortField);
            sortBuilder.order(CommonConstant.SORT_ORDER_ASC.equals(sortOrder) ? SortOrder.ASC : SortOrder.DESC);
        }

        // 分页
        PageRequest pageRequest = PageRequest.of(current, pageSize);
        // 把前面拼好的查询条件、分页、排序整合到一个 NativeSearchQuery 对象里。
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
                .withQuery(boolQueryBuilder)
                .withPageable(pageRequest)
                .withSorts(sortBuilder)
                .build();

        // 执行查询并拿回一批 QuestionEsDTO
        SearchHits<QuestionEsDTO> searchHits =
                elasticsearchRestTemplate.search(searchQuery, QuestionEsDTO.class);

        // 复用 MySQL 的分页对象，封装返回结果
        Page<Question> page = new Page<>();
        page.setTotal(searchHits.getTotalHits());
        
        List<Question> resourceList = new ArrayList<>();
        if (searchHits.hasSearchHits()) {
            List<SearchHit<QuestionEsDTO>> searchHitList = searchHits.getSearchHits();
            for (SearchHit<QuestionEsDTO> questionEsDTOSearchHit : searchHitList) {
                resourceList.add(QuestionEsDTO.dtoToObj(questionEsDTOSearchHit.getContent()));
            }
        }
        page.setRecords(resourceList);
        return page;
    }
```







# 题目批量管理

## 健壮性

### 更详细的参数校验

在第一版代码中已经提前对参数进行了非空校验，并且会提前检查题目题库是否存在。

```Java
        // 参数校验
        ThrowUtils.throwIf(CollUtil.isEmpty(questionIdList), ErrorCode.PARAMS_ERROR, "题目列表不能为空");
        ThrowUtils.throwIf(questionBankId <= 0, ErrorCode.PARAMS_ERROR, "题库 id 非法");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
```

------

但是我们还没有校验哪些⁢题目已经添加到题库中，对于这些题目，不‍必再执行插入关联记录的数据库操作。

- ***先拼接 `sql` 查询到已经存在于指定题库中的题目 id***

```java
        // 已经存在于该题库，且其题目 ID 在你给定集合里的那部分记录
        LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                .eq(QuestionBankQuestion::getQuestionBankId, questionBankId)
                .in(QuestionBankQuestion::getQuestionId, validQuestionIdList);
        List<QuestionBankQuestion> existQuestionList = this.list(lambdaQueryWrapper);
        // 提取已存在题目的 ID
        Set<Long> existQuestionIdSet = existQuestionList.stream()
                        .map(QuestionBankQuestion::getQuestionId)
                        .collect(Collectors.toSet());
```

- ***对于这些已经存在于题库中的题目，就不需要再次添加了***

```Java
        // 遍历原始的 validQuestionIdList（待插入的题目 ID 列表），只有当这个 ID 不在 existQuestionIdSet 中时，才保留
        validQuestionIdList = validQuestionIdList.stream().filter(questionId -> {
            return !existQuestionIdSet.contains(questionId);
        }).collect(Collectors.toList());
        ThrowUtils.throwIf(CollUtil.isEmpty(validQuestionIdList), ErrorCode.PARAMS_ERROR, "所有题目都已存在于题库中");
```





### 添加更详细的异常捕获

```Java
            try {
                boolean result = this.save(questionBankQuestion);
                if (!result) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
                }
            } catch (DataIntegrityViolationException e) {
                log.error("数据库唯一键冲突或违反其他完整性约束，题目 id: {}, 题库 id: {}, 错误信息: {}",
                        questionId, questionBankId, e.getMessage());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目已存在于该题库，无法重复添加");
            } catch (DataAccessException e) {
                log.error("数据库连接问题、事务问题等导致操作失败，题目 id: {}, 题库 id: {}, 错误信息: {}",
                        questionId, questionBankId, e.getMessage());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "数据库操作失败");
            } catch (Exception e) {
                // 捕获其他异常，做通用处理
                log.error("添加题目到题库时发生未知错误，题目 id: {}, 题库 id: {}, 错误信息: {}",
                        questionId, questionBankId, e.getMessage());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
            }
```

在插入数据库之前，可以添加唯一键冲突，数据库连接等问题。





## 稳定性

### 避免长事务

假设操作 10w ⁠条数据，其中有 1 条数据操作异常，如果是长事务，那么修改的 10w﻿ 条数据都需要回滚

而**分批事务仅需⁢回滚一批既可**，降低长事务带来的资源‍消耗，同时也提升了稳定性。



编写一个新的方法，用于对某一批操作进行事务管理：（在刚才批量把题目添加到题库的方法中，把循环插入数据库的内容分批出来）

```Java
        // 分批处理，避免长事务，每次处理 1000 条数据
        int batchSize = 1000;
        int totalQuestionListSize = validQuestionIdList.size();
        for(int i = 0; i < totalQuestionListSize; i+=batchSize){
            // 生成每批次的数据
            List<Long> subList = validQuestionIdList.subList(i, Math.min(i + batchSize, totalQuestionListSize));  // 防止越界
            List<QuestionBankQuestion> questionBankQuestions = subList.stream()
                    .map(questionId -> {
                        QuestionBankQuestion questionBankQuestion = new QuestionBankQuestion();
                        questionBankQuestion.setQuestionBankId(questionBankId);
                        questionBankQuestion.setQuestionId(questionId);
                        questionBankQuestion.setUserId(loginUser.getId());
                        return questionBankQuestion;
                    }).collect(Collectors.toList());

            //使用事务处理每批数据插入到数据库
            batchAddQuestionsToBankInner(questionBankQuestions);
        }

    }


    @Transactional(rollbackFor = Exception.class)
    public void batchAddQuestionsToBankInner(List<QuestionBankQuestion> questionBankQuestions) {
        for (QuestionBankQuestion questionBankQuestion : questionBankQuestions) {
            long questionId = questionBankQuestion.getQuestionId();
            long questionBankId = questionBankQuestion.getQuestionBankId();
            try {
                boolean result = this.save(questionBankQuestion);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
            } catch (DataIntegrityViolationException e) {
                log.error("数据库唯一键冲突或违反其他完整性约束，题目 id: {}, 题库 id: {}, 错误信息: {}",
                        questionId, questionBankId, e.getMessage());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目已存在于该题库，无法重复添加");
            } catch (DataAccessException e) {
                log.error("数据库连接问题、事务问题等导致操作失败，题目 id: {}, 题库 id: {}, 错误信息: {}",
                        questionId, questionBankId, e.getMessage());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "数据库操作失败");
            } catch (Exception e) {
                // 捕获其他异常，做通用处理
                log.error("添加题目到题库时发生未知错误，题目 id: {}, 题库 id: {}, 错误信息: {}",
                        questionId, questionBankId, e.getMessage());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
            }
        }
    }
```



- **注意：Spring 事务依赖于代理机制，如果按照上面的写法直接调用事务方法，不会通过 Spring 的代理，不会触发事务。因此在原本直接调用的位置要改为：` AopContext.currentProxy()` 。然后由于是自己的实现类调用自己的接口下的实现方法，因此要在事务方法上加上 `@Override` 注解。**

```Java
            //使用事务处理没批数据插入到数据库
            QuestionBankQuestionService questionBankQuestionService = (QuestionBankQuestionServiceImpl) AopContext.currentProxy();
            questionBankQuestionService.batchAddQuestionsToBankInner(questionBankQuestions);
```

 



## 性能优化

### 批量操作

当前每道题目是单独插入到数据库中的，会产生频繁的数据库交互，利用 `MyBatis-Plus` 提供的 `saveBatch` 方法批量插入，在事务的方法中去掉 for 循环。

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void batchAddQuestionsToBankInner(List<QuestionBankQuestion> questionBankQuestions) {
    try {
        boolean result = this.saveBatch(questionBankQuestions);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
    } catch (DataIntegrityViolationException e) {
        log.error("数据库唯一键冲突或违反其他完整性约束, 错误信息: {}", e.getMessage());
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目已存在于该题库，无法重复添加");
    } catch (DataAccessException e) {
        log.error("数据库连接问题、事务问题等导致操作失败, 错误信息: {}", e.getMessage());
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "数据库操作失败");
    } catch (Exception e) {
        // 捕获其他异常，做通用处理
        log.error("添加题目到题库时发生未知错误，错误信息: {}", e.getMessage());
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
    }
}
```





### SQL 优化

有一个最基本的 SQL 优化原则，不要使用 `select *` 来查询数据，只查出需要的字段即可。

```java
        // 检查题目 id 是否存在
        List<Question> questionList = questionService.listByIds(questionIdList);
```

上面就是我们只需要题目的 id，但是由于使用了 list 方法，进行了一次全表扫描，大幅降低性能，改为：（查询传过来要批处理的 id 是否等于题目表内的 id）

```Java
        // 检查题目 id 是否存在
        LambdaQueryWrapper<Question> questionLambdaQueryWrapper = Wrappers.lambdaQuery(Question.class)
                .select(Question::getId)
                .in(Question::getId, questionIdList);
        List<Question> questionList = questionService.list(questionLambdaQueryWrapper);
```





### 并发编程

在操作较多，追求处理时间的情况下，可以通过并发编程让没批操作同时执行，而不是一批处理完再执行下一批。

可以利用并发包中的 **CompletableFuture + 线程池** 来并发处理多个任务。

- ***示例：***

```Java
List<CompletableFuture<Void>> futures = new ArrayList<>();

for (List<Long> subList : splitList(validQuestionIdList, 1000)) {
    // 通过 runAsync() 方法创建异步任务，用 lambda 表达式写法，把要执行的任务传递给 runAsync()
    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
        processBatch(subList, questionBankId, loginUser);
    });
    futures.add(future);
}

// 最后通过 CompletableFuture.allOf 方法阻塞等待，只有所有的子任务都完成，才会执行后续代码
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
```



在项目中，可以把按批次把数据存储到数据库中的操作异步化：

需要用到 futures 列表的原因是：为了在最后能够**统一等待**并**汇总**所有批次的执行结果，保证整个“分批插入”流程正确结束才往下走。

```Java
            // 异步处理每批数据，将任务添加到异步任务列表
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                questionBankQuestionService.batchAddQuestionsToBankInner(questionBankQuestions);
            }, customExecutor);
            // 添加到任务列表里
            futures.add(future);
        }

        // 等待所有批次完成操作，统一捕获
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        // 关闭线程池
        customExecutor.shutdown();
```





### 数据库连接池调优

在配置文件中可以通过以下几个参数来调整 Druid 连接池的核心数，最大连接数等信息：

```yml
druid:
  # 配置初始化大小、最小、最大
  initial-size: 1
  minIdle: 1
  max-active: 1
  # 配置获取连接等待超时的时间(单位：毫秒)
  max-wait: 2000
```







# 自动缓存热门题库

对于面试⁠刷题平台，为了减少用户加载网页和题目的时间，可以**对经﻿常访问的数据（比如首页数据⁢和题目）进行缓存**，而不是每‍次都从数据库进行查询。

对于我们的面试刷题平台，鱼皮推广的时候可能会重点宣传某一个题库或题目，大家可以直接点链接进入特定的内容，这个内容就会成为 **热点数据** 。我们如果能够预判到热点数据，可以提前人工给数据加缓存，也就是 **缓存预热** 。

很多企业级项目都会有热点问题，例如微博，一个明星出了某些花边新闻，那么这条微博就会成为热点，此时系统需要 **自动发现** **这个热点**，将其做多级缓存来顶住大流量访问的压力。



- ***具体设计规则：对于获取题库详情的请求，如果 5 秒内访问 >= 1﻿0 次，就要使用本地缓存将⁢题库详情缓存 10 分钟，‍之后都从本地缓存读取。***





## (面试) hotKey

hotkey 依赖于 `Etcd`，`Etcd` 作为一个⁠高性能的配置中心，可以以极小的资源占用，提供高效的监听订阅服务。主要﻿用于存放规则配置，各 worker⁢ 的 ip 地址，以及探测出的热 ‍key、手工添加的热 key 等。



下载后解压压缩包，会得到 3 个脚本：

- `etcd`：`etcd` 服务本身
- `etcdctl`：客户端，用于操作 `etcd`，比如读写数据
- `etcdutl`：备份恢复工具



执行 `et⁠cd` 脚本后，可以启动 `etcd` 服﻿务，服务默认占用 ⁢2379 和 23‍80 端口，作用分别如下：

- 2379：提供 HTTP API 服务，和 `etcdctl` 交互
- 2380：集群中节点间通讯





- ***引入方式***

将官方的源码下载下来之后打成 jar 包，然后在项目中把这个打好的 jar 包放入到 lib 目录下。

然后在 `pom.xml` 中引入本地的 `hotKey` jar包.

```xml
<dependency>
  <artifactId>hotkey-client</artifactId>
  <groupId>com.jd.platform.hotkey</groupId>
  <version>0.0.4-SNAPSHOT</version>
  <scope>system</scope>
  <systemPath>${project.basedir}/lib/hotkey-client-0.0.4-SNAPSHOT.jar</systemPath>
</dependency>
```



- ***编写 Config 配置类***

```Java
@Configuration
@ConfigurationProperties(prefix = "hotkey")
@Data
public class HotKeyConfig {

    /**
     * Etcd 服务器完整地址
     */
    private String etcdServer = "http://127.0.0.1:2379";

    /**
     * 应用名称
     */
    private String appName = "mianshitiku";

    /**
     * 本地缓存最大数量
     */
    private int caffeineSize = 10000;

    /**
     * 批量推送 key 的间隔时间
     */
    private long pushPeriod = 1000L;

    /**
     * 初始化 hotkey
     */
    @Bean
    public void initHotkey() {
        ClientStarter.Builder builder = new ClientStarter.Builder();
        ClientStarter starter = builder.setAppName(appName)
                .setCaffeineSize(caffeineSize)
                .setPushPeriod(pushPeriod)
                .setEtcdServer(etcdServer)
                .build();
        starter.startPipeline();
    }
}
```

- ***同时配置 `application.yml`：***

```yml
# 热 key 探测
hotkey:
  app-name: mianshitiku
  caffeine-size: 10000
  push-period: 1000
  etcd-server: http://localhost:2379
```





### 开发模式

只要使用 `J⁠dHotKeyStore` 这个类即可非常方便﻿地判断 key 是否成⁢为热点和获取热点 ke‍y 对应的本地缓存。

- ***这个类主要有如下 4 个方法可供使用：***

```java
boolean JdHotKeyStore.isHotKey(String key)
Object JdHotKeyStore.get(String key)
void JdHotKeyStore.smartSet(String key, Object value)
Object JdHotKeyStore.getValue(String key)
```

1）`boolean isHotKey(String key)`

该方法会返回该 key ⁠是否是热 key，如果是返回 true，如果不是返回 false，并且会将 key 上报到探﻿测集群进行数量计算。该方法通常用于判断只需要判⁢断 key 是否热、不需要缓存 value 的‍场景，如刷子用户、接口访问频率等。

2）`Object get(String key)`

该方法返回该 ⁠key 本地缓存的 value 值，可用于判断是热 ke﻿y 后，再去获取本地缓存的 ⁢value 值，通常用于 `r‍edis` 热 key 缓存。

3）`void smartSet(String key, Object value)`

方法给热 ⁠key 赋值 value，如果是热 ﻿key，该方法才会⁢赋值，非热 key‍，什么也不做

4）`Object getValue(String key)`

该方法是一个整⁠合方法，相当于 `isHotKey` 和 get 两个方﻿法的整合，该方法直接返回本⁢地缓存的 value。 如‍果是热 key，则存在两种情况

1. 是返回 value
2. 是返回 null

返回 null 是因为⁠尚未给它 set 真正的 value，返回非 null 说明已经调用过 set 方法﻿了，本地缓存 value 有值了。 如果不⁢是热 key，则返回 null，并且将 `k‍ey` 上报到探测集群进行数量探测。





- ***在 `HotKey` 监控平台配置 hotkey 的规则：***

```json
[
    {
        "duration": 600,
        "key": "bank_detail_",
        "prefix": true,
        "interval": 5,
        "threshold": 10,
        "desc": "热门题库缓存"
    }
]
```

判断 `bank_detail_` 开头的 key，如果 5 秒访问 10 次，就会被推送到 `jvm` 内存中，将这个热 key 缓存 10 分钟。





- ***在本项目中如果某个题库的访问量突然升高，就可以用 hotkey：（在根据 id 获取题库的方法中）***

```Java
        // 生成 key
        String key = "bank_detail_" + id;
        // 判断拿到的 key，即题库是否为热 key
        if(JdHotKeyStore.isHotKey(key)){
            // 先尝试从本地缓存中取值，避免走后续的数据库或分布式缓存查询。
            Object cachedQuestionBankVO = JdHotKeyStore.get(key);
            // 如果本地缓存中有值，直接返回缓存的值
            return ResultUtils.success((QuestionBankVO) cachedQuestionBankVO);
        }


		// 原本查询数据的逻辑（查数据库）
		

		// 根据当前的热点 Key 列表，决定是否要把刚查到的数据放到本地缓存。
        JdHotKeyStore.smartSet(key, questionBankVO);

        // 获取封装类
        return ResultUtils.success(questionBankVO);
```



- ***注意接下来要使用就得先运行 `JDHotKey` 源码的程序，把 `workers` 节点启动起来，在源码的配置文件中有访问监控面板的端口号***







# 流量安全优化

## 熔断机制

熔断机制的目的是 **避免当下游服务发生异常时，整个系统继续耗费资源重复发起失败请求**，从而防止连锁故障。

即当一个接口出现问题的时候，先返回一些虚假数据给前端，能够保证其他接口的功能能够正常使用。

------

- ***熔断恢复机制：***熔断并非永久状态。在一段时间后，熔断器会进入 **半开状态**，允许少量请求测试服务的健康情况。如果恢复正常，熔断器将关闭，恢复正常服务调用；如果仍有问题，则继续保持熔断。





## (面试)Sentinel 组件

Sentinel ⁠是阿里巴巴开源的限流、熔断、降级组件，旨在为分布式系统提供可靠的保护﻿机制。它设计用于解决高并发流量下的稳定性⁢问题。





- ***核心概念***

1）资源：⁠表示要保护的业务逻辑或代码块。

使用 Sentinel 来进行资源保护，主要分为几个步骤:

1. 定义资源
2. 定义规则
3. 检验规则是否生效

------

2）规则：使用规则来定义对资﻿源的保护策略。

- 限流规则：用于控制流量的规则，设置 QPS（每秒查询量）等参数，防止系统过载。
- 熔断规则：用于实现熔断降级的规则，当某个资源的异常比例或响应时间超过阈值时，触发熔断，短时间内不再访问该资源。
- 系统规则：根据系统的整体负载（如 CPU 使用率、内存使用率等）进行保护，适合在系统级别进行流量控制。
- 热点参数规则：用于限制某个方法的某些热点参数的访问频率，避免某些参数导致流量过大。
- 授权规则：用于定义黑白名单的授权规则，控制资源访问的权限。

------

***3）滑动窗口机制：***

如下图，对于 `windowIntervalMs` 秒内的数据，底层会先将这 `windowIntervalMs` 秒**分成 `sampleCount` 段（窗口的大小）**，对每段可以理解为一个数组内的一个元素，不断向后滑动计算这个时间段内的访问次数。

<img src="./面试刷题平台.assets/屏幕截图 2025-08-04 105830.png" style="zoom: 33%;" />





### 入门demo

- ***先引入 `Sentinel` 依赖 ：***

```xml
        <dependency>
            <groupId>com.alibaba.csp</groupId>
            <artifactId>sentinel-core</artifactId>
            <version>1.8.8</version>
        </dependency>
```



- ***配置资源和规则：***

```java
    public static void main(String[] args) {
        // 配置规则.
        initFlowRules();

        while (true) {
            // 自定义资源，资源名称为 "HelloWorld"
            try (Entry entry = SphU.entry("HelloWorld")) {
                // 被保护的逻辑
                System.out.println("hello world");
            } catch (BlockException ex) {
                // 处理被流控的逻辑
                System.out.println("blocked!");
            }
        }
    }

    private static void initFlowRules(){
        // 定义规则列表
        List<FlowRule> rules = new ArrayList<>();
        FlowRule rule = new FlowRule();
        // 指定规则设置的资源对象为 "HelloWorld"
        rule.setResource("HelloWorld");
        // 指定 QPS
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        // 设置每秒的访问资源的次数不得超过20次
        rule.setCount(20);
        
        rules.add(rule);
        FlowRuleManager.loadRules(rules);
    }
```





### 控制台的使用

- ***下载好Sentinel的控制台 jar 包后，然后到存放 jar 包的目录下执行：***

```bat
java -Dserver.port=8131 -jar sentinel-dashboard-1.8.8.jar
```

------

访问端口：http://localhost:8131，此时是不会显示任何东西的，需要将控制台和程序进行下一步连接：

------

- ***引入 `Sentine﻿l` 控制台***

```xml
<dependency>
  <groupId>com.alibaba.csp</groupId>
  <artifactId>sentinel-transport-simple-http</artifactId>
  <version>1.8.8</version>
</dependency>
```

------

在程序中需要编辑配置，然后选择 **修改选项** ，**添加虚拟机选项**，添加上 `-Dcsp.sentinel.dashboard.server=localhost:8131` 来指定访问地址。





### 规则模式

在生产环境⁠中，官方推荐 push 模式，支持自定义﻿存储规则的配置中心，⁢控制台改变规则后，会‍ push 到配置中心。

<img src="./面试刷题平台.assets/屏幕截图 2025-08-04 114642.png" style="zoom:33%;" />





### 整合 SpringBoot

- ***引入依赖：***

```xml
        <!-- https://mvnrepository.com/artifact/com.alibaba.cloud/spring-cloud-starter-alibaba-sentinel -->
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
            <version>2021.0.5.0</version>
        </dependency>
```

此时就可以访问接口了，`SpringBoot` 整合的 `Sentinel` 是可以自己将所有接口都自动识别为资源的。







## 项目整合 Sentinel

资源：`li⁠stQuestionBankVOBy﻿Page` 接口

目的：控制⁠对耗时较长的、经常访问的接口的请求频﻿率，防止过多请求导致系⁢统过载。

限流规则：

- 策略：整个接口每秒钟不超过 10 次请求
- 阻塞操作：提示“系统压力过大，请耐心等待”

熔断规则：

- 熔断条件：如果接口异常率超过 10%，或者慢调用（响应时长 > 3 秒）的比例大于 20%，触发 60 秒熔断。
- 熔断操作：直接返回本地数据（缓存或空数据）

**开发模式：用注解定义资源 + 基于控制台定义规则**





1. 找到 `listQuestionBankVOByPage` 接口，添加注解：

```Java
    @SentinelResource(value = "listQuestionBankVOByPage",
            blockHandler = "handleBlockException",
            fallback = "handleFallback")
```

第一个参数**指定资源名称**（指定为方法名）；第二个参数是**通过哪个名称的方法去处理阻塞操作**；第三个参数是**指定调用哪个方法去进行降级操作**

------

2. 实现刚才在注解中定义的两个方法。**注意方法的返回值和传入的参数要和指定的资源方法一致（记得需要多一个异常参数）**

```Java
/**
 * listQuestionBankVOByPage 降级操作：直接返回本地数据
 */
public BaseResponse<Page<QuestionBankVO>> handleFallback(@RequestBody QuestionBankQueryRequest questionBankQueryRequest, HttpServletRequest request, Throwable ex) {
    // 可以返回本地数据或空数据
    return ResultUtils.success(null);
}

/**
 * listQuestionBankVOByPage 流控操作
 * 限流：提示“系统压力过大，请耐心等待”
 */
public BaseResponse<Page<QuestionBankVO>> handleBlockException(@RequestBody QuestionBankQueryRequest questionBankQueryRequest, HttpServletRequest request, BlockException ex) {
    // 限流操作
    return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统压力过大，请耐心等待");
}

```

------

3. 在控制台中新增熔断规则

- ***慢调用：***对于当前这个资源的调用时长超过3秒的比例超过了20%，就触发熔断时长60秒，至少要有5次请求，20秒一个统计周期

<img src="./面试刷题平台.assets/屏幕截图 2025-08-04 142209.png" style="zoom:33%;" />

- ***异常比例：***调用该接口的异常率超过了10%，就出发熔断60秒

<img src="./面试刷题平台.assets/屏幕截图 2025-08-04 142643.png" style="zoom:33%;" />

- ***限流配置：***整个接口每秒钟不超过12次请求

<img src="./面试刷题平台.assets/屏幕截图 2025-08-10 164257.png" style="zoom: 25%;" />





- ***注意：`BlockException` 所抛出的异常不仅仅是限流异常，还有触发熔断机制之后的降级异常。***
- ​	    ***`handleFallback` 处理的是业务本身的异常。***

代码修改为：

```Java
/**
 * listQuestionBankVOByPage 流控操作
 * 限流：提示“系统压力过大，请耐心等待”
 * 熔断：执行降级操作
 */
public BaseResponse<Page<QuestionBankVO>> handleBlockException(@RequestBody QuestionBankQueryRequest questionBankQueryRequest, HttpServletRequest request, BlockException ex) {
    // 如果检测到是降级异常，调用handleFallback
    if (ex instanceof DegradeException) {
        return handleFallback(questionBankQueryRequest, request, ex);
    }
    // 限流操作
    return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统压力过大，请耐心等待");
}
```







### (面试)热点参数限流

由于需要针对每个用户进一步精细化限流，而不是整体接口限流，可以采用 [热点参数限流机制](https://sentinelguard.io/zh-cn/docs/parameter-flow-control.html)，允许根据参数控制限流触发条件。

对于项目中的需求，可以将 IP 地址作为热点参数。

注意 catch 中要把三种异常全部描述清晰：**业务异常，降级异常，限流异常**。

```Java
        // 基于 IP 限流
        String remoteAddr = request.getRemoteAddr();
        Entry entry = null;
        try{
            // 1,指定资源名称；2,别人调用我们的资源就用 IN，反之用OUT；3,每调用一次Entry需要上报几次资源；4,把要传递的参数上报给Sentinel
            entry = SphU.entry("listQuestionVOByPage", EntryType.IN, 1, remoteAddr);

            // 查询数据库
            Page<Question> questionPage = questionService.page(new Page<>(current, size),
                    questionService.getQueryWrapper(questionQueryRequest));
            // 获取封装类
            return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
            
            // 捕捉限流和降级的异常
        }catch (Throwable ex){
            // 要判断是否是业务异常，自定义的方法中如果不上报业务异常给 Sentinel，会报错
            if(!BlockException.isBlockException(ex)){
                Tracer.trace(ex);  // 上报当前业务异常给 Sentinel
                return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
            }

            // 如果检测到是降级异常，调用handleFallback
            if (ex instanceof DegradeException) {
                return handleFallback(questionQueryRequest, request, ex);
            }
            // 限流操作
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "访问过于频繁，请稍后重试");
            
        }finally {
            // 释放资源
            if(entry != null){
                entry.exit(1, remoteAddr);
            }
        }
    }

    public BaseResponse<Page<QuestionVO>> handleFallback(@RequestBody QuestionQueryRequest questionQueryRequest, HttpServletRequest request, Throwable ex) {
        // 可以返回本地数据或空数据
        return ResultUtils.success(null);
    }
```



- ***每次都在控制台中手动修改参数未免有些繁琐，使用编程式方法自定义熔断，降级规则：***

```Java
@Component
public class SentinelRulesManager {

    @PostConstruct
    public void initRules() {
        initFlowRules();
        initDegradeRules();
    }

    // 限流规则
    public void initFlowRules() {
        // 单 IP 查看题目列表限流规则
        ParamFlowRule rule = new ParamFlowRule("listQuestionVOByPage")
                .setParamIdx(0) // 对第 0 个参数限流，即 IP 地址
                .setCount(60) // 每分钟最多 60 次
                .setDurationInSec(60); // 规则的统计周期为 60 秒
        ParamFlowRuleManager.loadRules(Collections.singletonList(rule));
    }

    // 降级规则
    public void initDegradeRules() {
        // 慢调用设置
        DegradeRule slowCallRule = new DegradeRule("listQuestionVOByPage")
                .setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType())
                .setCount(0.2) // 慢调用比例大于 20%
                .setTimeWindow(60) // 熔断持续时间 60 秒
                .setStatIntervalMs(30 * 1000) // 统计时长 30 秒
                .setMinRequestAmount(10) // 最小请求数
                .setSlowRatioThreshold(3); // 响应时间超过 3 秒

        // 异常比例设置
        DegradeRule errorRateRule = new DegradeRule("listQuestionVOByPage")
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(0.1) // 异常率大于 10%
                .setTimeWindow(60) // 熔断持续时间 60 秒
                .setStatIntervalMs(30 * 1000) // 统计时长 30 秒
                .setMinRequestAmount(10); // 最小请求数

        // 加载规则
        DegradeRuleManager.loadRules(Arrays.asList(slowCallRule, errorRateRule));
    }
}
```







# 动态 IP 黑名单过滤

通过 IP⁠ 封禁，可以有效拉黑攻击者，防止资源﻿被滥用，保障合法用⁢户的正常访问。



1. IP 黑名单存储在哪里？
2. 如何便捷地动态修改 IP 黑名单？
3. 黑白名单的判断逻辑应在哪里处理？
4. 使用何种数据结构保存黑名单？如何快速匹配用户请求的 IP 是否在黑名单中？



总结一下最终方案：

1）使用 `Nacos` 配置中心存储和管理 IP 黑名单

2）后端服务利用 Web 过滤器判断每个用户请求的 IP

3）后端服务利用布隆过滤器过滤 IP 黑名单





## 布隆过滤器

核心是会生成一个位数组：**原理是利用⁠多个哈希函数将元素映射到固定的点位上﻿（位数组中）**，因此⁢面对海量数据它占据‍的空间也非常小。



通过 **合理设计位数组的大小和哈希函数的个数**，可以控制 Bloom Filter 的误判率在一个可接受的范围内。例如，在很多实际场景中，可以将误判率控制在 **1%** 或更低。

- 假设场景 1：存储 1000 个元素，位数组大小为 10000 位，哈希函数数量为 7。误判率大约为 0.8%。
- 假设场景 2：存储 100000 个元素，位数组大小为 1,000,000 位，哈希函数数量为 7。误判率大约为 1%。
- 假设场景 3：存储 1,000,000 个元素，位数组大小为 10,000,000 位，哈希函数数量为 7。误判率大约为 1%。





## Nacos配置中心

`Nacos` 是一个更易于构建云原生应用的动态服务发现、配置管理和服务管理平台。

以单节点方式启动 `nacos`：到 bin 目录下执行：

```bat
startup.cmd -m standalone
```

访问控制台端口：`localhost:8848/nacos ` ，默认用户名和密码都为 `nacos`



- ***控制台进行黑名单的配置：***

<img src="./面试刷题平台.assets/屏幕截图 2025-08-04 170002.png" style="zoom:50%;" />



- ***引入依赖***

```xml
<dependency>
    <groupId>com.alibaba.boot</groupId>
    <artifactId>nacos-config-spring-boot-starter</artifactId>
    <version>0.2.12</version>
</dependency>
```



在 `application.yml` 配置文件中，配置如下：

```yml
# 配置中心
nacos:
  config:
    server-addr: 127.0.0.1:8848  # nacos 地址
    bootstrap:
      enable: true  # 预加载
    data-id: mianshitiku # 控制台填写的 Data ID
    group: DEFAULT_GROUP # 控制台填写的 group
    type: yaml  # 选择的文件格式
    auto-refresh: true # 开启自动刷新
```







### (面试)项目中运用 Nacos

当配置文件和依赖都配置好之后，**创建一个 `BlackIpUtils` 类来动态过滤修改黑名单**

```Java
@Slf4j
public class BlackIpUtils {

    // 创建布隆过滤器，过滤黑名单用户
    private static BitMapBloomFilter bloomFilter;

    // 判断 ip 是否在黑名单内
    public static boolean isBlackIp(String ip){
        return bloomFilter.contains(ip);
    }

    // 重建 ip 黑名单，使之能够动态修改
    public static void rebuildBlackIp(String configInfo){
        if(StrUtil.isBlank(configInfo)){
            configInfo = "{}";
        }
        // 解析 yaml 文件
        Yaml yaml = new Yaml();
        // 将 configInfo 转换成 Map 对象
        Map map = yaml.loadAs(configInfo, Map.class);
        // 拿取黑名单中的值
        List<String> blackIpList = (List<String>) map.get("blackIpList");

        // 构造布隆过滤器
        synchronized (BlackIpUtils.class){
            // 如果拿到的黑名单不为空
            if(CollectionUtil.isNotEmpty(blackIpList)){
                // 确定布隆过滤器内部的位数组长度
                BitMapBloomFilter bitMapBloomFilter = new BitMapBloomFilter(958506);
                for(String ip : blackIpList){
                    bitMapBloomFilter.add(ip);
                }
                bloomFilter = bitMapBloomFilter;
            }else{
                // 黑名单为空，减少布隆过滤器为数组的长度
                bloomFilter = new BitMapBloomFilter(100);
            }
        }
    }
}
```



- ***创建一个监听类用来调用以上创建的动态修改 `黑名单ip` 的方法***

```java
@Slf4j
@Component
public class NacosListener implements InitializingBean {

    @NacosInjected
    private ConfigService configService;

    /**
     * 如同控制台的配置，配置唯一的 Data-id 和 group
     */
    @Value("${nacos.config.data-id}")
    private String dataId;
    @Value("${nacos.config.group}")
    private String group;

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("nacos 监听器启动");

        String config = configService.getConfigAndSignListener(dataId, group, 3000L, new Listener() {
            final ThreadFactory threadFactory = new ThreadFactory() {
                private final AtomicInteger poolNumber = new AtomicInteger(1);
                @Override
                public Thread newThread(@NotNull Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("refresh-ThreadPool" + poolNumber.getAndIncrement());
                    return thread;
                }
            };
            final ExecutorService executorService = Executors.newFixedThreadPool(1, threadFactory);

            // 通过线程池异步处理黑名单变化的逻辑
            @Override
            public Executor getExecutor() {
                return executorService;
            }

            // 监听后续黑名单变化
            @Override
            public void receiveConfigInfo(String configInfo) {
                log.info("监听到配置信息变化：{}", configInfo);
                BlackIpUtils.rebuildBlackIp(configInfo);
            }
        });
        // 初始化黑名单
        BlackIpUtils.rebuildBlackIp(config);
    }
}
```



- ***黑名单应该对所有请求生⁠效（不止是 Controller 的接口），所以基于 WebFilter 实现而不是 A﻿OP 切面。WebFilter 的优先级高于⁢ @Aspect 切面，因为它在整个 Web‍ 请求生命周期中更早进行处理。***

```java
@WebFilter(urlPatterns = "/*", filterName = "blackIpFilter")
public class BlackIpFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException, IOException, ServletException {

        String ipAddress = NetUtils.getIpAddress((HttpServletRequest) servletRequest);
        // 如果发现是黑名单用户
        if (BlackIpUtils.isBlackIp(ipAddress)) {
            servletResponse.setContentType("text/json;charset=UTF-8");
            servletResponse.getWriter().write("{\"errorCode\":\"-1\",\"errorMsg\":\"黑名单IP，禁止访问\"}");
            return;
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }
}
```





## 基于DFA算法的黑名单过滤器

在 `BlackIpUtils` 工具类下，通过 `Hutool` 工具类内置的 `WordTree` 类，来创建一刻DFA树.

通过 `.addWords()` 方法，可以把黑名单加入到这棵树中

通过 `.match(ip)` 方法，可以从树中检测当前的 `ip` 是否属于黑名单

```java
    // DFA 树结构
    private static WordTree wordTree = new WordTree();

    // 判断 ip 是否在黑名单内
    public static boolean isBlackIp(String ip){
        if(StrUtil.isBlank(ip)){
            return false;
        }
        // DFA匹配规则查看是否完全匹配黑名单中的 ip
        return wordTree.match(ip) != null;
    }
```

```java
        // 构造布隆过滤器
        synchronized (BlackIpUtils.class){
//            // 如果拿到的黑名单不为空
//            if(CollectionUtil.isNotEmpty(blackIpList)){
//                // 确定布隆过滤器内部的位数组长度
//                BitMapBloomFilter bitMapBloomFilter = new BitMapBloomFilter(958506);
//                for(String ip : blackIpList){
//                    bitMapBloomFilter.add(ip);
//                }
//                bloomFilter = bitMapBloomFilter;
//            }else{
//                // 黑名单为空，减少布隆过滤器为数组的长度
//                bloomFilter = new BitMapBloomFilter(100);
//            }

            // 如果拿到的黑名单中的 ip 不为空，就更新DFA树
            WordTree newTree = new WordTree();
            if(CollUtil.isNotEmpty(blackIpList)){
                newTree.addWords(blackIpList);
            }
            wordTree = newTree;
        }
```







# (简历)AI功能扩展

- ***需求分析：***

1）AI 可以自动生成面试题，生成题目（题库）

2）AI 自动解答面试题，生成题解



- ***方案设计***

技术选型：AI的选型？`Deepseek`，火山引擎等.

------

**1、接入 AI**

使用 SD⁠K，封装自己的 AI 工具类（`AiM﻿anager` 便于⁢项目统一调用）、封‍装配置类

**2、AI 生成题目【仅管理员可用】**

编写一段 ⁠Prompt，让用户输入想生成的题目﻿内容，并且保存到数⁢据库中

1. 明确输入，一般情况下，尽量详细
2. 明确输出，建议给 AI 输出示例，便于处理 AI 分析的内容
3. 明确参数：题目的类别方向，比如 Java；题目的生成数量，比如 20
4. 设置系统预设，让 AI 的领域更专业

- ***prompt如下：***

```
你是一位专业的程序员面试官，你要帮我生成 {数量} 道 {方向} 面试题，要求输出格式如下：

1. 什么是 Java 中的反射？
2. Java 8 中的 Stream API 有什么作用？
3. xxxxxx

除此之外，请不要输出任何多余的内容，不要输出开头、也不要输出结尾，只输出上面的列表。

接下来我会给你要生成的题目{数量}、以及题目{方向}
```





**3，编写 `AIConfig` 配置类，让其能够去读取配置文件内的内容，并且创建连接池和去连接 ai 的 `url` 地址**

```java
@Configuration
@ConfigurationProperties("ai")
public class AiConfig {

    private String apiKey;

    /**
     * AI 请求客户端
     */
    @Bean
    public ArkService aiService() {
        ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
        Dispatcher dispatcher = new Dispatcher();
        ArkService service = ArkService.builder().dispatcher(dispatcher).connectionPool(connectionPool)
                .baseUrl("https://ark.cn-beijing.volces.com/api/v3")
                .apiKey(apiKey)
                .build();
        return service;
    }
}
```



**4，封装 `AiManager` 类用来调用 Ai 接口，并获取到 Ai 的响应字符串**

利用方法重载，区分给 ai 设置系统预设，和不给 ai 设置系统预设两中情况

```Java
public class AiManager {

    @Resource
    private ArkService aiService;

    private final String DEFAULT_MODEL = "deepseek-v3-250324";

    public String doChat(String userPrompt){
        return doChat("", userPrompt, DEFAULT_MODEL);
    }

    /**
     * 调用 AI 接口，获取 AI 的响应字符串
     * @return
     */
    public String doChat(String systemPrompt, String userPrompt, String model){
        // 构造消息列表
        final List<ChatMessage> messages = new ArrayList<>();
        final ChatMessage systemMessage = ChatMessage.builder().role(ChatMessageRole.SYSTEM).content(systemPrompt).build();
        final ChatMessage userMessage = ChatMessage.builder().role(ChatMessageRole.USER).content(userPrompt).build();
        messages.add(systemMessage);
        messages.add(userMessage);

        // 构造发送请求的请求体
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
//                .model("")
                .model(model)
                .messages(messages)
                .build();

        // 遍历每个响应得到输出
        List<ChatCompletionChoice> choices = aiService.createChatCompletion(chatCompletionRequest).getChoices();
        if(CollUtil.isEmpty(choices)){
            return (String) choices.get(0).getMessage().getContent();
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 调用失败，没有返回结果");

//        aiService.shutdownExecutor();
    }
}
```









