import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOpExpr;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOperator;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLVariantRefExpr;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlUpdateStatement;
import com.alibaba.druid.util.JdbcConstants;
import com.alicloud.openservices.tablestore.model.ColumnValue;
import com.alicloud.openservices.tablestore.model.condition.ColumnCondition;
import com.alicloud.openservices.tablestore.model.condition.CompositeColumnValueCondition;
import com.alicloud.openservices.tablestore.model.condition.SingleColumnValueCondition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class TableStoreSqlParser {

  public static void main(String[] args) {
    final String dbType = JdbcConstants.MYSQL; // 可以是ORACLE、POSTGRESQL、SQLSERVER、ODPS等
    String sql0 = "update t set c1=:c1,c2=:c2";
    String sql1 = "update t set c1=:c1,c2=:c2 where id=:id";
    String sql2 = "update t set c1=:c1,c2=:c2 where (id=:id)or(id1=:id1 and id2=:id2)";
    List<SQLStatement> stmtList0 = SQLUtils.parseStatements(sql0, dbType);
    List<SQLStatement> stmtList1 = SQLUtils.parseStatements(sql1, dbType);
    List<SQLStatement> stmtList2 = SQLUtils.parseStatements(sql2, dbType);
    MySqlUpdateStatement statement = (MySqlUpdateStatement)stmtList2.get(0);
   // statement.getWhere()
    SQLBinaryOpExpr op = (SQLBinaryOpExpr) statement.getWhere();
    Map<String, Object> parameters = new HashMap<String, Object>();
    parameters.put(":c1", 11);
    parameters.put(":c2", "gd");
    parameters.put(":c2", "gd");
    parameters.put(":id", "id");
    parameters.put(":id1", "id1");
    parameters.put(":id2", "id2");

    ColumnCondition condition = ColumnValueConditionExpression.parse(op, parameters);
    System.out.println(condition);
  }

  public static class ColumnValueConditionExpression {
    static Map<SQLBinaryOperator, SingleColumnValueCondition.CompareOperator> map = new HashMap<SQLBinaryOperator, SingleColumnValueCondition.CompareOperator>();
    static {
      map.put(SQLBinaryOperator.LessThanOrEqual, SingleColumnValueCondition.CompareOperator.LESS_EQUAL);
      map.put(SQLBinaryOperator.LessThan, SingleColumnValueCondition.CompareOperator.LESS_THAN);
      map.put(SQLBinaryOperator.Equality, SingleColumnValueCondition.CompareOperator.EQUAL);
      map.put(SQLBinaryOperator.NotEqual, SingleColumnValueCondition.CompareOperator.NOT_EQUAL);
      map.put(SQLBinaryOperator.GreaterThan, SingleColumnValueCondition.CompareOperator.GREATER_THAN);
      map.put(SQLBinaryOperator.GreaterThanOrEqual, SingleColumnValueCondition.CompareOperator.GREATER_EQUAL);
    }

    private static ColumnValue getColumnValue(String attributeName, Map<String, Object> parameters){
      if(!parameters.containsKey(attributeName)) return null;
      Object value = parameters.get(attributeName);
      if(value instanceof Byte[]) {
        byte[] data = new byte[((Byte[]) value).length];
        for (int i = 0; i < ((Byte[]) value).length; i++) {
          data[i] = ((Byte[])value)[i];
        }
        return ColumnValue.fromBinary(data);
      }
      if (value instanceof Long) {
        return ColumnValue.fromLong((Long)value);
      }
      if (value instanceof Boolean) {
        return ColumnValue.fromBoolean((Boolean) value);
      }
      if (value instanceof Double) {
        return ColumnValue.fromDouble((Double) value);
      }if (value instanceof String) {
        return ColumnValue.fromString((String)value);
      }
      throw new UnsupportedOperationException("unsupport data type");
    }

    public static ColumnCondition parse(SQLExpr expr, Map<String, Object> parameters){
      @SuppressWarnings("unchecked")
      Stack<Object> stack = new Stack();
      stack.clear();
      postTraverseBuildColumnCondition(expr, parameters, stack);
      return (ColumnCondition)stack.pop();
    }

    private static void  postTraverseBuildColumnCondition(SQLExpr expr, Map<String, Object> parameters, Stack<Object> stack) {
      if (expr != null) {
        if (expr instanceof SQLBinaryOpExpr)
          postTraverseBuildColumnCondition(((SQLBinaryOpExpr) expr).getLeft(), parameters, stack);
        if (expr instanceof SQLBinaryOpExpr)
          postTraverseBuildColumnCondition(((SQLBinaryOpExpr) expr).getRight(), parameters, stack);
        if(expr instanceof SQLIdentifierExpr){
          stack.push(expr);
        }
        if(expr instanceof SQLVariantRefExpr){
          stack.push(expr);
        }
        if(expr instanceof SQLBinaryOpExpr){
          SQLBinaryOpExpr exprCalc = (SQLBinaryOpExpr)expr;
          SQLBinaryOperator operator = exprCalc.getOperator();
          if(operator == SQLBinaryOperator.LessThan
            || operator == SQLBinaryOperator.LessThanOrEqual
            || operator == SQLBinaryOperator.Equality
            || operator == SQLBinaryOperator.NotEqual
            || operator == SQLBinaryOperator.GreaterThan
            || operator == SQLBinaryOperator.GreaterThanOrEqual){
            Object expr0 = stack.pop();
            Object expr1 = stack.pop();
            if(expr0 instanceof SQLVariantRefExpr && expr1 instanceof SQLIdentifierExpr){
              SingleColumnValueCondition condition = new SingleColumnValueCondition(((SQLIdentifierExpr) expr1).getName(), map.get(operator), getColumnValue(((SQLVariantRefExpr) expr0).getName(), parameters));
              stack.push(condition);
            }
          }else if(operator == SQLBinaryOperator.BooleanAnd ||
            operator == SQLBinaryOperator.BooleanOr){
            Object expr0 = stack.pop();
            Object expr1 = stack.pop();
            CompositeColumnValueCondition condition = null;
            if(operator == SQLBinaryOperator.BooleanAnd) {
              condition = new CompositeColumnValueCondition(CompositeColumnValueCondition.LogicOperator.AND);
            }else if(operator == SQLBinaryOperator.BooleanOr) {
              condition = new CompositeColumnValueCondition(CompositeColumnValueCondition.LogicOperator.OR);
            }
            condition.addCondition((ColumnCondition) expr0);
            condition.addCondition((ColumnCondition)expr1);
            stack.push(condition);
          }
          else if(operator == SQLBinaryOperator.IsNot){
            Object expr0= stack.pop();
            CompositeColumnValueCondition condition = null;
              condition = new CompositeColumnValueCondition(CompositeColumnValueCondition.LogicOperator.NOT);
            condition.addCondition((ColumnCondition) expr0);
            stack.push(condition);
          }
        }
      }
    }
  }
}
