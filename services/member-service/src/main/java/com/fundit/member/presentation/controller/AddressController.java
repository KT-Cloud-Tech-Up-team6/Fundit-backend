package com.fundit.member.presentation.controller;

import com.fundit.member.application.address.AddressService;
import com.fundit.member.infrastructure.security.CurrentMember;
import com.fundit.member.presentation.dto.AddressListItemResponse;
import com.fundit.member.presentation.dto.AddressRegisterRequest;
import com.fundit.member.presentation.dto.AddressRegisterResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public List<AddressListItemResponse> list(@CurrentMember UUID accountId) {
        return addressService.list(accountId).stream()
                .map(a -> new AddressListItemResponse(a.id(), a.recipientName(), a.phoneNumber(), a.zipcode(),
                        a.addressLine1(), a.addressLine2(), a.isDefault()))
                .toList();
    }

    @PostMapping
    public AddressRegisterResponse register(@CurrentMember UUID accountId, @Valid @RequestBody AddressRegisterRequest request) {
        var result = addressService.register(accountId, new AddressService.RegisterCommand(
                request.recipientName(), request.phoneNumber(), request.zipcode(),
                request.addressLine1(), request.addressLine2(), request.isDefault()));
        return new AddressRegisterResponse(result.id(), result.recipientName(), result.isDefault());
    }
}
