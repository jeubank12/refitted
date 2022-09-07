import { combineReducers } from "@reduxjs/toolkit";

import lambdaSlice from "./lambda/lambdaSlice";

export default combineReducers({
  lambda: lambdaSlice
})