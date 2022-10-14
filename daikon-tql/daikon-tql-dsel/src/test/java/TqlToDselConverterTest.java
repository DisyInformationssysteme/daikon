import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.talend.tqldsel.tqltodsel.TqlToDselConverter.wrapNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.talend.maplang.el.parser.model.ELNode;
import org.talend.maplang.el.parser.model.ELNodeType;
import org.talend.tql.excp.TqlException;
import org.talend.tql.model.Expression;
import org.talend.tql.parser.Tql;
import org.talend.tqldsel.tqltodsel.TqlToDselConverter;

public class TqlToDselConverterTest {

    static Map<String, String> fieldToType;

    @BeforeAll
    static void setUp() {
        final HashMap<String, String> fToType = new HashMap<>();
        fToType.put("name", "STRING");
        fToType.put("total", "INTEGER");
        fToType.put("isActivated", "BOOLEAN");
        fToType.put("money", "DECIMAL");
        fToType.put("special", "semanticType1");
        fieldToType = Collections.unmodifiableMap(fToType);
    }

    @Test
    public void testParseSingleNot() {
        final String query = "not (field1=123 and field2<124)";
        ELNode actual = TqlToDselConverter.convertForDb(query);

        List<ELNode> lst = new ArrayList<>();
        lst.add(buildNode(ELNodeType.EQUAL, "==", "field1", "123"));
        lst.add(buildNode(ELNodeType.LOWER_THAN, "<", "field2", "124"));
        ELNode andNode = new ELNode(ELNodeType.AND, "&&");
        andNode.addChildren(lst);
        ELNode notNode = new ELNode(ELNodeType.NOT, "!");
        notNode.addChild(andNode);

        assertEquals(wrapNode(notNode), actual);
    }

    @Test
    public void testParseIsNullWithTqlExpression() {
        final String tqlQuery = "field1 is null";
        final Expression tqlExpression = Tql.parse(tqlQuery);
        ELNode actual = TqlToDselConverter.convertForDb(tqlExpression);
        ELNode nullNode = buildNode(ELNodeType.FUNCTION_CALL, "isNull", "field1", null);

        assertEquals(wrapNode(nullNode), actual);
    }

    @Test
    public void testParseIsNull() {
        final String tqlQuery = "field1 is null";
        ELNode actual = TqlToDselConverter.convertForDb(tqlQuery);
        ELNode nullNode = buildNode(ELNodeType.FUNCTION_CALL, "isNull", "field1", null);

        assertEquals(wrapNode(nullNode), actual);
    }

    @Test
    public void testParseNotIsNull() {
        final String tqlQuery = "not(field1 is null)";
        ELNode actual = TqlToDselConverter.convertForDb(tqlQuery);

        ELNode notNode = new ELNode(ELNodeType.NOT, "!");
        ELNode nullNode = buildNode(ELNodeType.FUNCTION_CALL, "isNull", "field1", null);
        notNode.addChild(nullNode);

        assertEquals(wrapNode(notNode), actual);
    }

    @Test
    public void testParseIsEmpty() {
        final String tqlQuery = "field1 is empty";
        ELNode actual = TqlToDselConverter.convertForDb(tqlQuery);
        ELNode isEmptyNode = buildNode(ELNodeType.FUNCTION_CALL, "isEmpty", "field1", null);

        assertEquals(wrapNode(isEmptyNode), actual);
    }

    @Test
    public void testParseIsEmptyAnd() {
        final String tqlQuery = "(field1 is empty) and ((field2 is null))";
        ELNode actual = TqlToDselConverter.convertForDb(tqlQuery);
        ELNode isEmptyNode = buildNode(ELNodeType.FUNCTION_CALL, "isEmpty", "field1", null);
        ELNode isNull = buildNode(ELNodeType.FUNCTION_CALL, "isNull", "field2", null);
        ELNode andNode = new ELNode(ELNodeType.AND, "&&");
        andNode.addChild(isEmptyNode);
        andNode.addChild(isNull);

        assertEquals(wrapNode(andNode), actual);
    }

    @Test
    public void testParseIsEmptyWithAllFields() {
        final String tqlQuery = "* is empty";
        ELNode actual = TqlToDselConverter.convertForDb(tqlQuery, fieldToType);

        ELNode hasEmptyNode = new ELNode(ELNodeType.FUNCTION_CALL, "hasEmpty");
        hasEmptyNode.addChild(new ELNode(ELNodeType.STRING_LITERAL, "'*'"));

        assertEquals(wrapNode(hasEmptyNode).toString(), actual.toString());
    }

    @Test
    public void testParseIsEmptyWithAllFieldsForRuntime() {
        final String tqlQuery = "* is empty";
        ELNode actual = TqlToDselConverter.convertForRuntime(tqlQuery, fieldToType);

        ELNode orNode = new ELNode(ELNodeType.OR, "||");

        ELNode isInvalidNode1 = new ELNode(ELNodeType.FUNCTION_CALL, "isEmpty");
        isInvalidNode1.addChild(new ELNode(ELNodeType.INTEGER_LITERAL, "special"));

        orNode.addChild(isInvalidNode1);

        ELNode isInvalidNode2 = new ELNode(ELNodeType.FUNCTION_CALL, "isEmpty");
        isInvalidNode2.addChild(new ELNode(ELNodeType.INTEGER_LITERAL, "total"));

        orNode.addChild(isInvalidNode2);

        ELNode isInvalidNode3 = new ELNode(ELNodeType.FUNCTION_CALL, "isEmpty");
        isInvalidNode3.addChild(new ELNode(ELNodeType.INTEGER_LITERAL, "money"));
        orNode.addChild(isInvalidNode3);

        ELNode isInvalidNode4 = new ELNode(ELNodeType.FUNCTION_CALL, "isEmpty");
        isInvalidNode4.addChild(new ELNode(ELNodeType.STRING_LITERAL, "name"));
        orNode.addChild(isInvalidNode4);

        ELNode isInvalidNode5 = new ELNode(ELNodeType.FUNCTION_CALL, "isEmpty");
        isInvalidNode5.addChild(new ELNode(ELNodeType.INTEGER_LITERAL, "isActivated"));
        orNode.addChild(isInvalidNode5);

        assertEquals(wrapNode(orNode).toString(), actual.toString());
    }

    @Test
    public void testParseTqlLiteralWithSingleQuote() {
        final String tqlQuery = "name = 'abc\\'def'";
        ELNode actual = TqlToDselConverter.convertForDb(tqlQuery);

        ELNode regexNode = new ELNode(ELNodeType.EQUAL);
        regexNode.addChild(new ELNode(ELNodeType.HPATH, "name"));
        regexNode.addChild(new ELNode(ELNodeType.STRING_LITERAL, "'abc\\'def'"));

        assertEquals(wrapNode(regexNode), actual);
    }

    @Test
    public void testParseRegEx() {
        final String tqlQuery = "name ~ '^[A-Z][a-z]*$'";
        ELNode actual = TqlToDselConverter.convertForDb(tqlQuery);

        ELNode regexNode = new ELNode(ELNodeType.FUNCTION_CALL, "matches");
        regexNode.addChild(new ELNode(ELNodeType.HPATH, "name"));
        regexNode.addChild(new ELNode(ELNodeType.STRING_LITERAL, "'^[A-Z][a-z]*$'"));

        assertEquals(wrapNode(regexNode), actual);
    }

    @Test
    public void testParseRegEx2() {
        final String tqlQuery = "name ~ '\\d'";
        ELNode actual = TqlToDselConverter.convertForDb(tqlQuery);

        ELNode regexNode = new ELNode(ELNodeType.FUNCTION_CALL, "matches");
        regexNode.addChild(new ELNode(ELNodeType.HPATH, "name"));
        regexNode.addChild(new ELNode(ELNodeType.STRING_LITERAL, "'\\d'"));

        assertEquals(wrapNode(regexNode), actual);
    }

    @Test
    public void testParseRegExWithSingleQuote() {
        final String tqlQuery = "name ~ '\\d\\'\\w'";
        ELNode actual = TqlToDselConverter.convertForDb(tqlQuery);

        ELNode regexNode = new ELNode(ELNodeType.FUNCTION_CALL, "matches");
        regexNode.addChild(new ELNode(ELNodeType.HPATH, "name"));
        regexNode.addChild(new ELNode(ELNodeType.STRING_LITERAL, "'\\d\\'\\w'"));

        assertEquals(wrapNode(regexNode), actual);
    }

    @Test
    public void testParseTqlWordComplies() {
        final String tqlQuery = "name wordComplies '[word]'";
        ELNode actual = TqlToDselConverter.convertForDb(tqlQuery);

        ELNode regexNode = new ELNode(ELNodeType.FUNCTION_CALL, "wordComplies");
        regexNode.addChild(new ELNode(ELNodeType.HPATH, "name"));
        regexNode.addChild(new ELNode(ELNodeType.STRING_LITERAL, "'[word]'"));

        assertEquals(wrapNode(regexNode), actual);
    }

    @Test
    public void testParseTqlWordCompliesWithSingleQuote() {
        final String tqlQuery = "name wordComplies '[word]\\'[word]'";
        ELNode actual = TqlToDselConverter.convertForDb(tqlQuery);

        ELNode regexNode = new ELNode(ELNodeType.FUNCTION_CALL, "wordComplies");
        regexNode.addChild(new ELNode(ELNodeType.HPATH, "name"));
        regexNode.addChild(new ELNode(ELNodeType.STRING_LITERAL, "'[word]\\'[word]'"));

        assertEquals(wrapNode(regexNode), actual);
    }

    @Test
    public void testParseTqlComplies() {
        final String tqlQuery = "name complies 'aaa'";
        ELNode actual = TqlToDselConverter.convertForDb(tqlQuery);

        ELNode regexNode = new ELNode(ELNodeType.FUNCTION_CALL, "complies");
        regexNode.addChild(new ELNode(ELNodeType.HPATH, "name"));
        regexNode.addChild(new ELNode(ELNodeType.STRING_LITERAL, "'aaa'"));

        assertEquals(wrapNode(regexNode), actual);
    }

    @Test
    public void testParseTqlCompliesWithSingleQuote() {
        final String tqlQuery = "name complies 'aaa\\'aaa'";
        ELNode actual = TqlToDselConverter.convertForDb(tqlQuery);

        ELNode regexNode = new ELNode(ELNodeType.FUNCTION_CALL, "complies");
        regexNode.addChild(new ELNode(ELNodeType.HPATH, "name"));
        regexNode.addChild(new ELNode(ELNodeType.STRING_LITERAL, "'aaa\\'aaa'"));

        assertEquals(wrapNode(regexNode), actual);
    }

    @Test
    public void testParseContains() {
        final String tqlQuery = "name contains 'am'";
        ELNode actual = TqlToDselConverter.convertForDb(tqlQuery);

        ELNode containsNode = new ELNode(ELNodeType.FUNCTION_CALL, "contains");
        containsNode.addChild(new ELNode(ELNodeType.HPATH, "name"));
        containsNode.addChild(new ELNode(ELNodeType.STRING_LITERAL, "'am'"));

        assertEquals(wrapNode(containsNode), actual);
    }

    @Test
    public void testParseContainsWithSingleQuote() {
        final String tqlQuery = "name contains 'I\\'m'";
        ELNode actual = TqlToDselConverter.convertForDb(tqlQuery);

        ELNode containsNode = new ELNode(ELNodeType.FUNCTION_CALL, "contains");
        containsNode.addChild(new ELNode(ELNodeType.HPATH, "name"));
        containsNode.addChild(new ELNode(ELNodeType.STRING_LITERAL, "'I\\'m'"));

        assertEquals(wrapNode(containsNode), actual);
    }

    @Test
    public void testParseContainsException() {
        final String tqlQuery = "name contains";
        assertThrows(TqlException.class, () -> TqlToDselConverter.convertForDb(tqlQuery));
    }

    @Test
    public void testParseContainsIgnoreCase() {
        final String tqlQuery = "name containsIgnoreCase 'am'";
        ELNode actual = TqlToDselConverter.convertForDb(tqlQuery);

        ELNode containsNode = new ELNode(ELNodeType.FUNCTION_CALL, "contains");
        containsNode.addChild(new ELNode(ELNodeType.HPATH, "name"));
        containsNode.addChild(new ELNode(ELNodeType.STRING_LITERAL, "'am'"));
        containsNode.addChild(new ELNode(ELNodeType.BOOLEAN_LITERAL, "false"));

        assertEquals(wrapNode(containsNode), actual);
    }

    @Test
    public void testParseContainsIgnoreCaseException() {
        final String tqlQuery = "name containsIgnoreCase";
        assertThrows(TqlException.class, () -> TqlToDselConverter.convertForDb(tqlQuery));
    }

    @Test
    public void testParseBetweenForString() {
        final String tqlQuery = "field1 between ['value1', 'value4']";
        ELNode actual = TqlToDselConverter.convertForDb(tqlQuery);

        ELNode containsNode = new ELNode(ELNodeType.FUNCTION_CALL, "between");
        containsNode.addChild(new ELNode(ELNodeType.HPATH, "field1"));
        containsNode.addChild(new ELNode(ELNodeType.STRING_LITERAL, "'value1'"));
        containsNode.addChild(new ELNode(ELNodeType.STRING_LITERAL, "'value4'"));

        assertEquals(wrapNode(containsNode), actual);
    }

    @Test
    public void testParseBetweenForInt() {
        final String tqlQuery = "field1 between [2, 51]";
        ELNode actual = TqlToDselConverter.convertForDb(tqlQuery);

        ELNode containsNode = new ELNode(ELNodeType.FUNCTION_CALL, "between");
        containsNode.addChild(new ELNode(ELNodeType.HPATH, "field1"));
        containsNode.addChild(new ELNode(ELNodeType.INTEGER_LITERAL, "2"));
        containsNode.addChild(new ELNode(ELNodeType.INTEGER_LITERAL, "51"));

        assertEquals(wrapNode(containsNode), actual);
    }

    @Test
    public void testParseBetweenForDouble() {
        final String tqlQuery = "field1 between [12.1822, 189.37]";
        ELNode actual = TqlToDselConverter.convertForDb(tqlQuery);

        ELNode containsNode = new ELNode(ELNodeType.FUNCTION_CALL, "between");
        containsNode.addChild(new ELNode(ELNodeType.HPATH, "field1"));
        containsNode.addChild(new ELNode(ELNodeType.DOUBLE_LITERAL, "12.1822"));
        containsNode.addChild(new ELNode(ELNodeType.DOUBLE_LITERAL, "189.37"));

        assertEquals(wrapNode(containsNode).toString(), actual.toString());
    }

    @Test
    public void testParseBetweenForMix() {
        final String tqlQuery = "field1 between [3182, 4722.189]";
        ELNode actual = TqlToDselConverter.convertForDb(tqlQuery);

        ELNode containsNode = new ELNode(ELNodeType.FUNCTION_CALL, "between");
        containsNode.addChild(new ELNode(ELNodeType.HPATH, "field1"));
        containsNode.addChild(new ELNode(ELNodeType.INTEGER_LITERAL, "3182"));
        containsNode.addChild(new ELNode(ELNodeType.DOUBLE_LITERAL, "4722.189"));

        assertEquals(wrapNode(containsNode).toString(), actual.toString());
    }

    @Test
    public void testParseSingleAnd() {
        final String query = "field1=123 and field2<124";
        ELNode actual = TqlToDselConverter.convertForDb(query);

        List<ELNode> lst = new ArrayList<>();
        lst.add(buildNode(ELNodeType.EQUAL, "==", "field1", "123"));
        lst.add(buildNode(ELNodeType.LOWER_THAN, "<", "field2", "124"));
        ELNode andNode = new ELNode(ELNodeType.AND, "&&");
        andNode.addChildren(lst);

        assertEquals(wrapNode(andNode), actual);
    }

    @Test
    public void testParseDoubleAnd() {
        final String query = "field1=123 and field2<124 and field3>125";
        ELNode actual = TqlToDselConverter.convertForDb(query);

        List<ELNode> lst = new ArrayList<>();
        lst.add(buildNode(ELNodeType.EQUAL, "==", "field1", "123"));
        lst.add(buildNode(ELNodeType.LOWER_THAN, "<", "field2", "124"));
        lst.add(buildNode(ELNodeType.GREATER_THAN, ">", "field3", "125"));
        ELNode andNode = new ELNode(ELNodeType.AND, "&&");
        andNode.addChildren(lst);

        assertEquals(wrapNode(andNode), actual);
    }

    @Test
    public void testParseSingleOr() {
        final String query = "field1=123 or field2<124";
        ELNode actual = TqlToDselConverter.convertForDb(query);

        List<ELNode> lst = new ArrayList<>();
        lst.add(buildNode(ELNodeType.EQUAL, "==", "field1", "123"));
        lst.add(buildNode(ELNodeType.LOWER_THAN, "<", "field2", "124"));
        ELNode orNode = new ELNode(ELNodeType.OR, "||");
        orNode.addChildren(lst);

        assertEquals(wrapNode(orNode), actual);
    }

    @Test
    public void testParseDoubleOr() {
        final String query = "field1=123 or field2<124 or field3>125";
        ELNode actual = TqlToDselConverter.convertForDb(query);

        List<ELNode> lst = new ArrayList<>();
        lst.add(buildNode(ELNodeType.EQUAL, "==", "field1", "123"));
        lst.add(buildNode(ELNodeType.LOWER_THAN, "<", "field2", "124"));
        lst.add(buildNode(ELNodeType.GREATER_THAN, ">", "field3", "125"));
        ELNode orNode = new ELNode(ELNodeType.OR, "||");
        orNode.addChildren(lst);

        assertEquals(wrapNode(orNode), actual);
    }

    @Test
    public void testParseAndOr() {
        final String query = "field1=123 and field2<124 or field3>125 and field4<=126";
        ELNode actual = TqlToDselConverter.convertForDb(query);

        List<ELNode> lst1 = new ArrayList<>();
        List<ELNode> lst2 = new ArrayList<>();
        lst1.add(buildNode(ELNodeType.EQUAL, "==", "field1", "123"));
        lst1.add(buildNode(ELNodeType.LOWER_THAN, "<", "field2", "124"));
        lst2.add(buildNode(ELNodeType.GREATER_THAN, ">", "field3", "125"));
        lst2.add(buildNode(ELNodeType.LOWER_OR_EQUAL, "<=", "field4", "126"));
        ELNode orNode = new ELNode(ELNodeType.OR, "||");
        ELNode andNode1 = new ELNode(ELNodeType.AND, "&&");
        ELNode andNode2 = new ELNode(ELNodeType.AND, "&&");
        andNode1.addChildren(lst1);
        andNode2.addChildren(lst2);
        orNode.addChild(andNode1);
        orNode.addChild(andNode2);

        assertEquals(wrapNode(orNode), actual);
    }

    @Test
    public void testParseLiteralComparisonEq() {
        final String query = "field1=123";
        ELNode actual = TqlToDselConverter.convertForDb(query);

        assertEquals(buildNodeIncludingRootAndExprBlock(ELNodeType.EQUAL, "==", "field1", "123"), actual);
    }

    @Test
    public void testParseLiteralComparisonNeq() {
        final String query = "field1!=123";
        ELNode actual = TqlToDselConverter.convertForDb(query);

        assertEquals(buildNodeIncludingRootAndExprBlock(ELNodeType.NOT_EQUAL, "!=", "field1", "123"), actual);
    }

    @Test
    public void testParseLiteralComparisonLt() {
        final String query = "field1<123";
        ELNode actual = TqlToDselConverter.convertForDb(query);

        assertEquals(buildNodeIncludingRootAndExprBlock(ELNodeType.LOWER_THAN, "<", "field1", "123"), actual);
    }

    @Test
    public void testParseLiteralComparisonGt() {
        final String query = "field1>123";
        ELNode actual = TqlToDselConverter.convertForDb(query);

        assertEquals(buildNodeIncludingRootAndExprBlock(ELNodeType.GREATER_THAN, ">", "field1", "123"), actual);
    }

    @Test
    public void testParseLiteralComparisonLet() {
        final String query = "field4<=123";
        ELNode actual = TqlToDselConverter.convertForDb(query);

        assertEquals(buildNodeIncludingRootAndExprBlock(ELNodeType.LOWER_OR_EQUAL, "<=", "field4", "123"), actual);
    }

    @Test
    public void testParseLiteralComparisonGet() {
        final String query = "field3>=123";
        ELNode actual = TqlToDselConverter.convertForDb(query);

        assertEquals(buildNodeIncludingRootAndExprBlock(ELNodeType.GREATER_OR_EQUAL, ">=", "field3", "123"), actual);
    }

    @Test
    public void testParseLiteralComparisonNegative() {
        final String query = "field2=-123";
        ELNode actual = TqlToDselConverter.convertForDb(query);

        assertEquals(buildNodeIncludingRootAndExprBlock(ELNodeType.EQUAL, "==", "field2", "-123"), actual);
    }

    @Test
    public void testParseInForInt() {
        final String query = "field1 in [89178, 12, 99, 2]";
        ELNode actual = TqlToDselConverter.convertForDb(query);

        ELNode inNode = new ELNode(ELNodeType.FUNCTION_CALL, "in");
        inNode.addChild(new ELNode(ELNodeType.HPATH, "field1"));
        inNode.addChild(new ELNode(ELNodeType.INTEGER_LITERAL, "89178"));
        inNode.addChild(new ELNode(ELNodeType.INTEGER_LITERAL, "12"));
        inNode.addChild(new ELNode(ELNodeType.INTEGER_LITERAL, "99"));
        inNode.addChild(new ELNode(ELNodeType.INTEGER_LITERAL, "2"));

        assertEquals(wrapNode(inNode), actual);
    }

    @Test
    public void testParseInForString() {
        final String query = "field1 in ['value1', 'value2']";
        ELNode actual = TqlToDselConverter.convertForDb(query);

        ELNode inNode = new ELNode(ELNodeType.FUNCTION_CALL, "in");
        inNode.addChild(new ELNode(ELNodeType.HPATH, "field1"));
        inNode.addChild(new ELNode(ELNodeType.STRING_LITERAL, "'value1'"));
        inNode.addChild(new ELNode(ELNodeType.STRING_LITERAL, "'value2'"));

        assertEquals(wrapNode(inNode), actual);
    }

    @Test
    public void testParseInForDouble() {
        final String query = "field1 in [525.87, 12, 99.20, 252.0]";
        ELNode actual = TqlToDselConverter.convertForDb(query);

        ELNode inNode = new ELNode(ELNodeType.FUNCTION_CALL, "in");
        inNode.addChild(new ELNode(ELNodeType.HPATH, "field1"));
        inNode.addChild(new ELNode(ELNodeType.DOUBLE_LITERAL, "525.87"));
        inNode.addChild(new ELNode(ELNodeType.DOUBLE_LITERAL, "12"));
        inNode.addChild(new ELNode(ELNodeType.DOUBLE_LITERAL, "99.20"));
        inNode.addChild(new ELNode(ELNodeType.DOUBLE_LITERAL, "252.0"));

        assertEquals(wrapNode(inNode).toString(), actual.toString());
    }

    @Test
    public void testParseIsValidWithTqlExpression() {
        final String tqlQuery = "name is valid";
        final Expression tqlExpression = Tql.parse(tqlQuery);
        ELNode actual = TqlToDselConverter.convertForDb(tqlExpression, fieldToType);

        ELNode isValidNode = new ELNode(ELNodeType.FUNCTION_CALL, "isValid");
        isValidNode.addChild(new ELNode(ELNodeType.HPATH, "name"));
        isValidNode.addChild(new ELNode(ELNodeType.STRING_LITERAL, "STRING"));

        assertEquals(wrapNode(isValidNode), actual);
    }

    @Test
    public void testParseIsValid() {
        final String tqlQuery = "name is valid";
        ELNode actual = TqlToDselConverter.convertForDb(tqlQuery, fieldToType);

        ELNode isValidNode = new ELNode(ELNodeType.FUNCTION_CALL, "isValid");
        isValidNode.addChild(new ELNode(ELNodeType.HPATH, "name"));
        isValidNode.addChild(new ELNode(ELNodeType.STRING_LITERAL, "STRING"));

        assertEquals(wrapNode(isValidNode), actual);
    }

    @Test
    public void testParseIsValidWithExpectedException() {
        final String tqlQuery = "unknown is valid";

        Exception exception = assertThrows(TqlException.class, () -> TqlToDselConverter.convertForDb(tqlQuery, fieldToType));
        assertEquals("Cannot find the type of the field 'unknown'", exception.getMessage());
    }

    @Test
    public void testParseIsInvalidWithTqlExpression() {
        final String tqlQuery = "name is invalid";
        final Expression tqlExpression = Tql.parse(tqlQuery);
        ELNode actual = TqlToDselConverter.convertForDb(tqlExpression, fieldToType);

        ELNode isInvalidNode = new ELNode(ELNodeType.FUNCTION_CALL, "isInvalid");
        isInvalidNode.addChild(new ELNode(ELNodeType.HPATH, "name"));
        isInvalidNode.addChild(new ELNode(ELNodeType.STRING_LITERAL, "STRING"));

        assertEquals(wrapNode(isInvalidNode), actual);
    }

    @Test
    public void testParseIsInvalid() {
        final String tqlQuery = "name is invalid";
        ELNode actual = TqlToDselConverter.convertForDb(tqlQuery, fieldToType);

        ELNode isInvalidNode = new ELNode(ELNodeType.FUNCTION_CALL, "isInvalid");
        isInvalidNode.addChild(new ELNode(ELNodeType.HPATH, "name"));
        isInvalidNode.addChild(new ELNode(ELNodeType.STRING_LITERAL, "STRING"));

        assertEquals(wrapNode(isInvalidNode), actual);
    }

    @Test
    public void testParseIsInvalidWithAllFields() {
        final String tqlQuery = "* is invalid";
        ELNode actual = TqlToDselConverter.convertForDb(tqlQuery, fieldToType);

        ELNode hasInvalidNode = new ELNode(ELNodeType.FUNCTION_CALL, "hasInvalid");
        hasInvalidNode.addChild(new ELNode(ELNodeType.STRING_LITERAL, "'*'"));

        assertEquals(wrapNode(hasInvalidNode).toString(), actual.toString());
    }

    @Test
    public void testParseIsInvalidWithAllFieldsForRuntime() {
        final String tqlQuery = "* is invalid";
        ELNode actual = TqlToDselConverter.convertForRuntime(tqlQuery, fieldToType);

        ELNode orNode = new ELNode(ELNodeType.OR, "||");

        ELNode isInvalidNode1 = new ELNode(ELNodeType.FUNCTION_CALL, "isInvalid");
        isInvalidNode1.addChild(new ELNode(ELNodeType.INTEGER_LITERAL, "special"));
        isInvalidNode1.addChild(new ELNode(ELNodeType.STRING_LITERAL, "'" + fieldToType.get("special") + "'"));

        orNode.addChild(isInvalidNode1);

        ELNode isInvalidNode2 = new ELNode(ELNodeType.FUNCTION_CALL, "isInvalid");
        isInvalidNode2.addChild(new ELNode(ELNodeType.INTEGER_LITERAL, "total"));
        isInvalidNode2.addChild(new ELNode(ELNodeType.STRING_LITERAL, "'" + fieldToType.get("total") + "'"));

        orNode.addChild(isInvalidNode2);

        ELNode isInvalidNode3 = new ELNode(ELNodeType.FUNCTION_CALL, "isInvalid");
        isInvalidNode3.addChild(new ELNode(ELNodeType.INTEGER_LITERAL, "money"));
        isInvalidNode3.addChild(new ELNode(ELNodeType.STRING_LITERAL, "'" + fieldToType.get("money") + "'"));
        orNode.addChild(isInvalidNode3);

        ELNode isInvalidNode4 = new ELNode(ELNodeType.FUNCTION_CALL, "isInvalid");
        isInvalidNode4.addChild(new ELNode(ELNodeType.STRING_LITERAL, "name"));
        isInvalidNode4.addChild(new ELNode(ELNodeType.STRING_LITERAL, "'" + fieldToType.get("name") + "'"));
        orNode.addChild(isInvalidNode4);

        ELNode isInvalidNode5 = new ELNode(ELNodeType.FUNCTION_CALL, "isInvalid");
        isInvalidNode5.addChild(new ELNode(ELNodeType.INTEGER_LITERAL, "isActivated"));
        isInvalidNode5.addChild(new ELNode(ELNodeType.STRING_LITERAL, "'" + fieldToType.get("isActivated") + "'"));
        orNode.addChild(isInvalidNode5);

        assertEquals(wrapNode(orNode).toString(), actual.toString());
    }

    @Test
    public void testParseIsInvalidWithExpectedException() {
        final String tqlQuery = "unknown is invalid";
        Exception exception = assertThrows(TqlException.class, () -> TqlToDselConverter.convertForDb(tqlQuery, fieldToType));
        assertEquals("Cannot find the 'type' of the field 'unknown'", exception.getMessage());
    }

    private ELNode buildNode(ELNodeType type, String image, String name, String value) {
        ELNode current = new ELNode(type, image);
        current.addChild(new ELNode(ELNodeType.HPATH, name));

        if (value != null) {
            current.addChild(new ELNode(ELNodeType.INTEGER_LITERAL, value));
        }

        return current;
    }

    private ELNode buildNodeIncludingRootAndExprBlock(ELNodeType type, String image, String name, String value) {
        ELNode root = new ELNode(ELNodeType.ROOT);
        ELNode expBlk = new ELNode(ELNodeType.EXPR_BLOCK);
        ELNode eq = new ELNode(type, image);
        eq.addChild(new ELNode(ELNodeType.HPATH, name));
        eq.addChild(new ELNode(ELNodeType.INTEGER_LITERAL, value));
        expBlk.addChild(eq);
        root.addChild(expBlk);

        return root;
    }

}
