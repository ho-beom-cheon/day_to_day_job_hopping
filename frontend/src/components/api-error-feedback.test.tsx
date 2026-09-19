import { fireEvent, render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { ApiError } from "@/api/client";
import { toApiErrorViewModel } from "@/mappers/api-error";
import { ApiErrorFeedback } from "./api-error-feedback";

it.each([412, 428])("maps HTTP %s into a refresh-first conflict experience", status => {
  const onRefresh = vi.fn();
  const model = toApiErrorViewModel(new ApiError(status, undefined, "trace-test"));
  render(<ApiErrorFeedback viewModel={model} onRefresh={onRefresh} />);
  expect(screen.getByRole("alert")).toHaveTextContent("정보가 변경되었어요");
  fireEvent.click(screen.getByRole("button", {name:"최신 정보 불러오기"}));
  expect(onRefresh).toHaveBeenCalledTimes(1);
});
