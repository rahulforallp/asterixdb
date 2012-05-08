package edu.uci.ics.asterix.om.typecomputer.impl;

import java.util.Iterator;

import org.apache.commons.lang3.mutable.Mutable;

import edu.uci.ics.asterix.om.base.AString;
import edu.uci.ics.asterix.om.constants.AsterixConstantValue;
import edu.uci.ics.asterix.om.typecomputer.base.IResultTypeComputer;
import edu.uci.ics.asterix.om.types.ARecordType;
import edu.uci.ics.asterix.om.types.IAType;
import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalExpressionTag;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.AbstractFunctionCallExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.AbstractLogicalExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.ConstantExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.IVariableTypeEnvironment;
import edu.uci.ics.hyracks.algebricks.core.algebra.metadata.IMetadataProvider;

public class ClosedRecordConstructorResultType implements IResultTypeComputer {

    public static final ClosedRecordConstructorResultType INSTANCE = new ClosedRecordConstructorResultType();

    @Override
    public IAType computeType(ILogicalExpression expression, IVariableTypeEnvironment env,
            IMetadataProvider<?, ?> metadataProvider) throws AlgebricksException {
        AbstractFunctionCallExpression f = (AbstractFunctionCallExpression) expression;
        int n = f.getArguments().size() / 2;
        String[] fieldNames = new String[n];
        IAType[] fieldTypes = new IAType[n];
        int i = 0;
        Iterator<Mutable<ILogicalExpression>> argIter = f.getArguments().iterator();
        while (argIter.hasNext()) {
            ILogicalExpression e1 = (AbstractLogicalExpression) argIter.next().getValue();
            if (e1.getExpressionTag() == LogicalExpressionTag.CONSTANT) {
                ConstantExpression nameExpr = (ConstantExpression) e1;
                fieldNames[i] = ((AString) ((AsterixConstantValue) nameExpr.getValue()).getObject()).getStringValue();
            } else {
                throw new AlgebricksException("Field name " + i + "(" + e1
                        + ") in call to closed-record-constructor is not a constant.");
            }
            ILogicalExpression e2 = (AbstractLogicalExpression) argIter.next().getValue();
            fieldTypes[i] = (IAType) env.getType(e2);
            i++;
        }
        return new ARecordType(null, fieldNames, fieldTypes, false);
    }
}
