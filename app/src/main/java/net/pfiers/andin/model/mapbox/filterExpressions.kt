package net.pfiers.andin.model.mapbox

import com.mapbox.mapboxsdk.style.expressions.Expression


fun udExpr() = Expression.get("custom_data")

fun getUserDataExpr(key: String) = Expression.get(
    key,
    udExpr()
)!!

fun hasUserDataExpr(key: String) = Expression.has(
    key,
    Expression.get("custom_data")
)!!

fun nullExpr() = Expression.get(
    "tyv7saczbs8yfhaaombry5l0vgpg6llos6or05et" // TODO: Do something better
)!!

fun levelWithinRangeExpr(level: Double) = Expression.any(
    Expression.all(
        Expression.not(Expression.has("levelTo", udExpr())),
        Expression.eq(
            getUserDataExpr("levelFrom"),
            Expression.literal(level)
        )
    ),
    Expression.all(
        Expression.has("levelTo", udExpr()),
        Expression.gte(
            Expression.literal(level),
            getUserDataExpr("levelFrom"),
        ),
        Expression.lte(
            Expression.literal(level),
            getUserDataExpr("levelTo"),
        )
    )
)!!
