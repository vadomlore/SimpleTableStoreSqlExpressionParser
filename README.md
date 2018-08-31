# SimpleTableStoreSqlExpressionParser
将类似SQL的条件语句解析成TableStore的ColumnCondition条件表达式

目前仅支持where条件表达式的多种条件组合，数据的组合方式类似
```
update t set c1=:c1,c2=:c2 where (id=:id)or(id1=:id1 and id2=:id2)
```
其中:id, :id1, :id2等使用Map<key,value>的方式作为key传入，value传入需要的值