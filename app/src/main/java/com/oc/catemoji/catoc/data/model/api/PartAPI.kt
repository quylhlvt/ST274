package com.oc.catemoji.catoc.data.model.api

data class PartAPI(  val colorArray: String,
                       val parts: String,
                       val position: Any,
                       val quantity: Int,
                       val level : Int)
typealias CharacterResponse = Map<String, List<PartAPI>>