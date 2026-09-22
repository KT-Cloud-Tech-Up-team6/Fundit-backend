package com.fundit.member.presentation.controller;

import com.fundit.member.application.address.AddressService;
import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.member.presentation.dto.AddressListItemResponse;
import com.fundit.member.presentation.dto.AddressRegisterRequest;
import com.fundit.member.presentation.dto.AddressRegisterResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public List<AddressListItemResponse> list(@LoginUser CurrentUser user) {
        return addressService.list(user.id()).stream()
                .map(AddressController::toResponse)
                .toList();
    }

    @PostMapping
    public AddressRegisterResponse register(@LoginUser CurrentUser user, @Valid @RequestBody AddressRegisterRequest request) {
        var result = addressService.register(user.id(), new AddressService.RegisterCommand(
                request.recipientName(), request.phoneNumber(), request.zipcode(),
                request.addressLine1(), request.addressLine2(), request.isDefault()));
        return new AddressRegisterResponse(result.id(), result.recipientName(), result.isDefault());
    }

    @PutMapping("/{addressId}")
    public AddressListItemResponse update(@LoginUser CurrentUser user, @PathVariable Long addressId,
                                          @Valid @RequestBody AddressRegisterRequest request) {
        return toResponse(addressService.update(user.id(), addressId, new AddressService.RegisterCommand(
                request.recipientName(), request.phoneNumber(), request.zipcode(),
                request.addressLine1(), request.addressLine2(), request.isDefault())));
    }

    @PatchMapping("/{addressId}/default")
    public AddressListItemResponse changeDefault(@LoginUser CurrentUser user, @PathVariable Long addressId) {
        return toResponse(addressService.changeDefault(user.id(), addressId));
    }

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@LoginUser CurrentUser user, @PathVariable Long addressId) {
        addressService.delete(user.id(), addressId);
    }

    private static AddressListItemResponse toResponse(AddressService.AddressItem a) {
        return new AddressListItemResponse(a.id(), a.recipientName(), a.phoneNumber(), a.zipcode(),
                a.addressLine1(), a.addressLine2(), a.isDefault());
    }
}
