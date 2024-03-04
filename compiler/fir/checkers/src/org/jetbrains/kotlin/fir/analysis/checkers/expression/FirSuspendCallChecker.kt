/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.expression

import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.analysis.getChild
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.isInline
import org.jetbrains.kotlin.fir.declarations.utils.isSuspend
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.references.FirResolvedCallableReference
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.references.resolved
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toFirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes
import org.jetbrains.kotlin.utils.addToStdlib.lastIsInstanceOrNull

object FirSuspendCallChecker : FirQualifiedAccessExpressionChecker(MppCheckerKind.Common) {
    private val BUILTIN_SUSPEND_NAME = StandardClassIds.Callables.suspend.callableName

    internal val KOTLIN_SUSPEND_BUILT_IN_FUNCTION_CALLABLE_ID = CallableId(StandardClassIds.BASE_KOTLIN_PACKAGE, BUILTIN_SUSPEND_NAME)

    override fun check(expression: FirQualifiedAccessExpression, context: CheckerContext, reporter: DiagnosticReporter) {
        val reference = expression.calleeReference.resolved ?: return
        val symbol = reference.resolvedSymbol as? FirCallableSymbol ?: return
        if (reference.name == BUILTIN_SUSPEND_NAME ||
            symbol is FirNamedFunctionSymbol && symbol.name == BUILTIN_SUSPEND_NAME
        ) {
            checkSuspendModifierForm(expression, reference, symbol, context, reporter)
        }

        if (reference is FirResolvedCallableReference) {
            checkCallableReference(expression, symbol, reporter, context)
            return
        }

        when (symbol) {
            is FirNamedFunctionSymbol -> if (!symbol.isSuspend) return
            is FirPropertySymbol -> if (symbol.callableId != StandardClassIds.Callables.coroutineContext) return
            else -> return
        }
        val enclosingSuspendFunction = findEnclosingSuspendFunction(context)
        if (enclosingSuspendFunction == null) {
            when (symbol) {
                is FirNamedFunctionSymbol -> reporter.reportOn(expression.source, FirErrors.ILLEGAL_SUSPEND_FUNCTION_CALL, symbol, context)
                is FirPropertySymbol -> reporter.reportOn(expression.source, FirErrors.ILLEGAL_SUSPEND_PROPERTY_ACCESS, symbol, context)
                else -> {
                }
            }
        } else {
            if (!checkNonLocalReturnUsage(enclosingSuspendFunction, context)) {
                reporter.reportOn(expression.source, FirErrors.NON_LOCAL_SUSPENSION_POINT, context)
            }
            if (isInScopeForDefaultParameterValues(enclosingSuspendFunction, context)) {
                reporter.reportOn(
                    expression.source,
                    FirErrors.UNSUPPORTED,
                    "suspend function calls in a context of default parameter value",
                    context
                )
            }
            if (!checkRestrictsSuspension(expression, enclosingSuspendFunction, symbol, context)) {
                reporter.reportOn(expression.source, FirErrors.ILLEGAL_RESTRICTED_SUSPENDING_FUNCTION_CALL, context)
            }
        }
    }

    private fun checkSuspendModifierForm(
        expression: FirQualifiedAccessExpression,
        reference: FirResolvedNamedReference,
        symbol: FirCallableSymbol<*>,
        context: CheckerContext,
        reporter: DiagnosticReporter
    ) {
        if (symbol.callableId == KOTLIN_SUSPEND_BUILT_IN_FUNCTION_CALLABLE_ID) {
            if (reference.name != BUILTIN_SUSPEND_NAME ||
                expression.explicitReceiver != null ||
                expression.formOfSuspendModifierForLambdaOrFun() == null
            ) {
                reporter.reportOn(expression.source, FirErrors.NON_MODIFIER_FORM_FOR_BUILT_IN_SUSPEND, context)
            }
        } else if (reference.name == BUILTIN_SUSPEND_NAME) {
            when (expression.formOfSuspendModifierForLambdaOrFun()) {
                SuspendCallArgumentKind.FUN -> {
                    reporter.reportOn(expression.source, FirErrors.MODIFIER_FORM_FOR_NON_BUILT_IN_SUSPEND_FUN, context)
                }
                SuspendCallArgumentKind.LAMBDA -> {
                    reporter.reportOn(expression.source, FirErrors.MODIFIER_FORM_FOR_NON_BUILT_IN_SUSPEND, context)
                }
                null -> {
                    // Nothing to do
                }
            }
        }
    }

    private fun FirQualifiedAccessExpression.formOfSuspendModifierForLambdaOrFun(): SuspendCallArgumentKind? {
        if (this !is FirFunctionCall) return null
        val reference = this.calleeReference
        if (reference is FirResolvedCallableReference) return null
        if (typeArguments.any { it.source != null }) return null
        if (arguments.singleOrNull().let { it is FirAnonymousFunctionExpression && it.isTrailingLambda }) {
            // No brackets should be in a selector call
            val callExpressionSource =
                if (explicitReceiver == null) source
                else source?.getChild(KtNodeTypes.CALL_EXPRESSION, index = 1, depth = 1)
            if (callExpressionSource?.getChild(KtNodeTypes.VALUE_ARGUMENT_LIST, depth = 1) == null) {
                return SuspendCallArgumentKind.LAMBDA
            }
        }
        if (origin == FirFunctionCallOrigin.Infix) {
            val lastArgument = arguments.lastOrNull()
            if (lastArgument is FirAnonymousFunctionExpression && source?.getChild(KtNodeTypes.PARENTHESIZED, depth = 1) == null) {
                return if (lastArgument.source?.lighterASTNode?.tokenType == KtStubElementTypes.FUNCTION) {
                    SuspendCallArgumentKind.FUN
                } else {
                    SuspendCallArgumentKind.LAMBDA
                }
            }
        }
        return null
    }

    private fun findEnclosingSuspendFunction(context: CheckerContext): FirFunction? {
        return context.containingDeclarations.lastOrNull {
            when (it) {
                is FirAnonymousFunction ->
                    if (it.isLambda) it.typeRef.coneType.isSuspendOrKSuspendFunctionType(context.session) else it.isSuspend
                is FirSimpleFunction ->
                    it.isSuspend
                else ->
                    false
            }
        } as? FirFunction
    }

    private fun isInScopeForDefaultParameterValues(enclosingSuspendFunction: FirFunction, context: CheckerContext): Boolean {
        val valueParameters = enclosingSuspendFunction.valueParameters
        for (declaration in context.containingDeclarations.asReversed()) {
            when {
                declaration is FirValueParameter && declaration in valueParameters && declaration.defaultValue != null -> return true
                declaration is FirAnonymousFunction && declaration.inlineStatus == InlineStatus.Inline -> continue
                declaration is FirFunction && !declaration.isInline -> return false
            }
        }
        return false
    }

    private fun checkNonLocalReturnUsage(enclosingSuspendFunction: FirFunction, context: CheckerContext): Boolean {
        val containingFunction = context.containingDeclarations.lastIsInstanceOrNull<FirFunction>() ?: return false
        return if (containingFunction is FirAnonymousFunction && enclosingSuspendFunction !== containingFunction) {
            containingFunction.inlineStatus.returnAllowed
        } else {
            enclosingSuspendFunction === containingFunction
        }
    }

    private fun checkRestrictsSuspension(
        expression: FirQualifiedAccessExpression,
        enclosingSuspendFunction: FirFunction,
        calledDeclarationSymbol: FirCallableSymbol<*>,
        context: CheckerContext
    ): Boolean {
        val session = context.session

        val enclosingSuspendFunctionDispatchReceiverOwnerSymbol =
            (enclosingSuspendFunction.dispatchReceiverType as? ConeClassLikeType)?.lookupTag?.toFirRegularClassSymbol(session)
        val enclosingSuspendFunctionExtensionReceiverOwnerSymbol = enclosingSuspendFunction.takeIf { it.receiverParameter != null }?.symbol

        val (dispatchReceiverExpression, extensionReceiverExpression, extensionReceiverParameterType) =
            expression.computeReceiversInfo(session, calledDeclarationSymbol)

        for (receiverExpression in listOfNotNull(dispatchReceiverExpression, extensionReceiverExpression)) {
            if (!receiverExpression.resolvedType.isRestrictSuspensionReceiver(session)) continue
            if (sameInstanceOfReceiver(receiverExpression, enclosingSuspendFunctionDispatchReceiverOwnerSymbol)) continue
            if (sameInstanceOfReceiver(receiverExpression, enclosingSuspendFunctionExtensionReceiverOwnerSymbol)) continue

            return false
        }

        if (enclosingSuspendFunctionExtensionReceiverOwnerSymbol?.resolvedReceiverTypeRef?.coneType?.isRestrictSuspensionReceiver(session) != true) {
            return true
        }

        if (sameInstanceOfReceiver(dispatchReceiverExpression, enclosingSuspendFunctionExtensionReceiverOwnerSymbol)) {
            return true
        }

        if (sameInstanceOfReceiver(extensionReceiverExpression, enclosingSuspendFunctionExtensionReceiverOwnerSymbol)) {
            if (extensionReceiverParameterType?.isRestrictSuspensionReceiver(session) == true) {
                return true
            }
        }
        return false
    }

    private fun ConeKotlinType.isRestrictSuspensionReceiver(session: FirSession): Boolean {
        when (this) {
            is ConeClassLikeType -> {
                val regularClassSymbol = fullyExpandedType(session).lookupTag.toFirRegularClassSymbol(session) ?: return false
                if (regularClassSymbol.getAnnotationByClassId(StandardClassIds.Annotations.RestrictsSuspension, session) != null) {
                    return true
                }
                return regularClassSymbol.resolvedSuperTypes.any { it.isRestrictSuspensionReceiver(session) }
            }
            is ConeTypeParameterType -> {
                return lookupTag.typeParameterSymbol.resolvedBounds.any { it.coneType.isRestrictSuspensionReceiver(session) }
            }
            else -> return false
        }
    }

    private fun sameInstanceOfReceiver(
        useSiteReceiverExpression: FirExpression?,
        declarationSiteReceiverOwnerSymbol: FirBasedSymbol<*>?
    ): Boolean {
        if (declarationSiteReceiverOwnerSymbol == null || useSiteReceiverExpression == null) return false
        if (useSiteReceiverExpression is FirThisReceiverExpression) {
            return useSiteReceiverExpression.calleeReference.boundSymbol == declarationSiteReceiverOwnerSymbol
        }
        return false
    }

    // Triple<DispatchReceiverValue, ExtensionReceiverValue, ExtensionReceiverParameterType>
    private fun FirQualifiedAccessExpression.computeReceiversInfo(
        session: FirSession,
        calledDeclarationSymbol: FirCallableSymbol<*>
    ): Triple<FirExpression?, FirExpression?, ConeKotlinType?> {
        val dispatchReceiver = dispatchReceiver
        if (this is FirImplicitInvokeCall &&
            dispatchReceiver != null && dispatchReceiver.resolvedType.isSuspendOrKSuspendFunctionType(session)
        ) {
            val variableForInvoke = dispatchReceiver
            val variableForInvokeType = variableForInvoke.resolvedType
            if (!variableForInvokeType.isExtensionFunctionType) return Triple(null, null, null)

            // `a.foo()` is resolved to invokeExtension, so it's been desugared to `foo.invoke(a)`
            // And we use the first argument (`a`) as an extension receiver
            return Triple(
                null,
                argumentList.arguments.getOrNull(0),
                variableForInvokeType.typeArguments.getOrNull(0) as? ConeKotlinType
            )
        }

        return Triple(
            dispatchReceiver,
            extensionReceiver,
            calledDeclarationSymbol.resolvedReceiverTypeRef?.coneType,
        )
    }

    private fun checkCallableReference(
        expression: FirQualifiedAccessExpression,
        symbol: FirCallableSymbol<*>,
        reporter: DiagnosticReporter,
        context: CheckerContext,
    ) {
        if (symbol.callableId == StandardClassIds.Callables.coroutineContext) {
            reporter.reportOn(expression.calleeReference.source, FirErrors.UNSUPPORTED, "Callable reference to suspend property", context)
        }
    }
}

private enum class SuspendCallArgumentKind {
    FUN, LAMBDA
}
